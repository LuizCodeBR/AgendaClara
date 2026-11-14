package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.luizcode.agenda.policy.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BookingPolicyConcurrencyTest extends ApiTestBase {
    @MockitoSpyBean BookingPolicyService policies;
    @MockitoSpyBean CatalogRepository catalogSpy;
    @MockitoSpyBean(name="deterministicClock") Clock testClock;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void defaults(){jdbc.update("update business set booking_notice_minutes=0,cancel_notice_minutes=0,reschedule_notice_minutes=0,booking_policy_version=0");}
    @AfterEach void restore(){reset(policies,catalogSpy,testClock);defaults();}
    BookingRequest requestWithVersion(int version){var r=request("09:00");return new BookingRequest(r.requestKey(),r.professionalId(),r.serviceId(),r.startsAt(),r.customerName(),r.customerPhone(),version);}
    @Test void bookingSnapshotCannotMixWithAConcurrentSettingsChange() throws Exception{
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var editorStarted=new CountDownLatch(1);
        doAnswer(i->{if(Thread.currentThread().getName().equals("policy-editor"))editorStarted.countDown();var result=i.callRealMethod();
            if(Thread.currentThread().getName().equals("policy-booking")){locked.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();}return result;}).when(policies).lock(BUSINESS);
        try(var pool=Executors.newFixedThreadPool(2)){
            var booking=pool.submit(()->{Thread.currentThread().setName("policy-booking");return schedule.book(catalog.business(BUSINESS),requestWithVersion(0));});
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var editor=pool.submit(()->{Thread.currentThread().setName("policy-editor");return policies.save(BUSINESS,new BookingPolicyService.Save(60,120,180,0));});
            assertThat(editorStarted.await(10,TimeUnit.SECONDS)).isTrue();assertThat(editor.isDone()).isFalse();release.countDown();
            assertThat(booking.get(15,TimeUnit.SECONDS).receipt().bookingPolicy()).isEqualTo(BookingPolicy.NONE);
            assertThat(editor.get(15,TimeUnit.SECONDS)).isEqualTo(new BookingPolicy(60,120,180,1));
        }finally{release.countDown();}
    }
    @Test void waitingBookingRechecksTheRevisionAfterAnEditorCommits() throws Exception{
        var attempted=new CountDownLatch(1);
        doAnswer(i->{if(Thread.currentThread().getName().equals("waiting-booking"))attempted.countDown();return i.callRealMethod();}).when(policies).lock(BUSINESS);
        try(var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"waiting-booking"))){
            var future=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
            new TransactionTemplate(transactions).execute(s->{policies.save(BUSINESS,new BookingPolicyService.Save(60,120,180,0));
                future.set(pool.submit(()->{try{schedule.book(catalog.business(BUSINESS),requestWithVersion(0));return 201;}catch(BusinessException ex){return ex.status().value();}}));
                try{assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException ex){throw new AssertionError(ex);}return true;});
            assertThat(future.get().get(15,TimeUnit.SECONDS)).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isZero();
        }
    }
    @Test void concurrentEditorsCannotOverwriteOneAnother() throws Exception{
        var start=new CountDownLatch(1);var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Integer>>();
            for(int value:List.of(60,120))futures.add(pool.submit(()->{ready.countDown();assertThat(start.await(10,TimeUnit.SECONDS)).isTrue();
                try{policies.save(BUSINESS,new BookingPolicyService.Save(value,value+60,value+120,0));return 200;}catch(BusinessException ex){return ex.status().value();}}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            var saved=policies.get(BUSINESS);assertThat(saved.version()).isEqualTo(1);
            assertThat(saved.cancellationNoticeMinutes()).isEqualTo(saved.bookingNoticeMinutes()+60);assertThat(saved.rescheduleNoticeMinutes()).isEqualTo(saved.bookingNoticeMinutes()+120);
        }finally{start.countDown();}
    }
    @Test void leadTimeIsCheckedAfterWaitingForTheProfessionalLock() throws Exception{
        policies.save(BUSINESS,new BookingPolicyService.Save(60,0,0,0));
        var attempted=new CountDownLatch(1);
        doAnswer(i->{if(Thread.currentThread().getName().equals("time-booking"))attempted.countDown();return i.callRealMethod();}).when(catalogSpy).lockProfessional(BUSINESS,PROFESSIONAL);
        try(var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"time-booking"))){
            var future=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
            new TransactionTemplate(transactions).execute(s->{catalog.lockProfessional(BUSINESS,PROFESSIONAL);
                future.set(pool.submit(()->{try{schedule.book(catalog.business(BUSINESS),requestWithVersion(1));return 201;}catch(BusinessException ex){return ex.status().value();}}));
                try{assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException ex){throw new AssertionError(ex);}
                doReturn(at("08:30").toInstant()).when(testClock).instant();return true;});
            assertThat(future.get().get(15,TimeUnit.SECONDS)).isEqualTo(409);
            assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isZero();
        }
    }
}
