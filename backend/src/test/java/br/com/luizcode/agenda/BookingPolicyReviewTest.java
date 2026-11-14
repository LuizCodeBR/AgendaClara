package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.customer.CustomerPortalService;
import br.com.luizcode.agenda.customer.CustomerPortalService.*;
import br.com.luizcode.agenda.policy.*;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class BookingPolicyReviewTest extends ApiTestBase {
    static final Instant NOW=Instant.parse("2030-06-03T10:00:00Z");
    @Autowired BookingPolicyService policies;
    @Autowired CustomerPortalService portal;
    @MockitoSpyBean ScheduleRepository appointments;
    @MockitoSpyBean CatalogRepository catalogSpy;
    @MockitoSpyBean(name="deterministicClock") Clock testClock;
    @BeforeEach void setup(){defaults();doReturn(NOW).when(testClock).instant();}
    @AfterEach void restore(){reset(appointments,catalogSpy,testClock);defaults();}
    void defaults(){jdbc.update("update business set booking_notice_minutes=0,cancel_notice_minutes=0,reschedule_notice_minutes=0,booking_policy_version=0");}
    void policy(int lead,int cancel,int move){policies.save(BUSINESS,new BookingPolicyService.Save(lead,cancel,move,0));}
    BookingOutcome book(){var r=request("09:00");return schedule.book(catalog.business(BUSINESS),new BookingRequest(r.requestKey(),r.professionalId(),r.serviceId(),r.startsAt(),r.customerName(),r.customerPhone(),1));}
    UUID reservation(){return jdbc.queryForObject("select id from appointment",UUID.class);}
    void delayAvailabilityUntil(Instant now){doAnswer(i->{var result=i.callRealMethod();doReturn(now).when(testClock).instant();return result;})
        .when(appointments).overlaps(eq(BUSINESS),eq(PROFESSIONAL),any(Instant.class),any(Instant.class),any(UUID.class));}
    void unchanged(UUID id){var a=appointments.appointment(BUSINESS,id);assertThat(a.startsAt()).isEqualTo(at("09:00").toInstant());assertThat(a.status()).isEqualTo("BOOKED");assertThat(a.version()).isZero();}
    @Test void bookingCannotCrossItsLeadDeadlineDuringAvailabilityValidation(){
        policy(1560,0,0);delayAvailabilityUntil(NOW.plusSeconds(1));
        assertThatThrownBy(this::book).hasMessageContaining("antecedência");
        assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isZero();
    }
    @Test void customerMoveCannotCrossItsOriginalDeadlineDuringAvailabilityValidation(){
        policy(0,0,1560);var receipt=book().receipt();var id=reservation();var secret=portal.open(receipt.customerAccessToken(),null).secret();
        delayAvailabilityUntil(NOW.plusSeconds(1));
        assertThatThrownBy(()->portal.move(secret,id,new Move(at("10:00"),0))).hasMessageContaining("prazo de remarcação");unchanged(id);
    }
    @Test void customerMoveCannotCrossTheNewSlotsLeadDeadlineDuringAvailabilityValidation(){
        policy(1620,0,0); // Book later first, then test moving to exactly 27 hours away.
        var r=request("11:00");var receipt=schedule.book(catalog.business(BUSINESS),new BookingRequest(r.requestKey(),r.professionalId(),r.serviceId(),r.startsAt(),r.customerName(),r.customerPhone(),1)).receipt();
        var id=reservation();var secret=portal.open(receipt.customerAccessToken(),null).secret();delayAvailabilityUntil(NOW.plusSeconds(1));
        assertThatThrownBy(()->portal.move(secret,id,new Move(at("10:00"),0))).hasMessageContaining("antecedência");
        assertThat(appointments.appointment(BUSINESS,id).startsAt()).isEqualTo(at("11:00").toInstant());assertThat(appointments.appointment(BUSINESS,id).version()).isZero();
    }
    @Test void cancellationDeadlineIsCheckedAtTheWriteAfterLoadingTheBusiness(){
        policy(0,1560,0);var receipt=book().receipt();var id=reservation();var secret=portal.open(receipt.customerAccessToken(),null).secret();
        doAnswer(i->{var result=i.callRealMethod();doReturn(NOW.plusSeconds(1)).when(testClock).instant();return result;}).when(catalogSpy).business(BUSINESS);
        assertThatThrownBy(()->portal.cancel(secret,id,new Change(0))).hasMessageContaining("prazo de cancelamento");unchanged(id);
    }
    @ParameterizedTest @ValueSource(strings={"bookingNoticeMinutes","cancellationNoticeMinutes","rescheduleNoticeMinutes","version"})
    void fractionalValuesAreRejectedInsteadOfTruncated(String field) throws Exception{
        var values=new LinkedHashMap<String,Object>();for(var name:List.of("bookingNoticeMinutes","cancellationNoticeMinutes","rescheduleNoticeMinutes","version"))values.put(name,0);
        values.put(field,-0.5);
        mvc.perform(put("/api/admin/business/booking-policy").with(user(owner())).with(csrf()).contentType("application/json").content(json(values))).andExpect(status().isBadRequest());
        assertThat(policies.get(BUSINESS)).isEqualTo(BookingPolicy.NONE);
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void policyLockCannotBeUsedWithoutATransaction(){
        assertThatThrownBy(()->policies.lock(BUSINESS)).isInstanceOf(IllegalStateException.class);
    }
    @Test void policyLockCannotBeUsedInAReadOnlyTransaction(){
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);tx.setReadOnly(true);
        assertThatThrownBy(()->tx.execute(s->policies.lock(BUSINESS))).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void customerDeadlinesAreRecheckedAfterWaitingForTheProfessional(boolean move) throws Exception{
        policy(0,60,60);var receipt=book().receipt();var id=reservation();
        doReturn(at("07:59").toInstant()).when(testClock).instant();
        var secret=portal.open(receipt.customerAccessToken(),null).secret();
        var attempted=new java.util.concurrent.CountDownLatch(1);
        doAnswer(i->{if(Thread.currentThread().getName().equals("review-customer"))attempted.countDown();return i.callRealMethod();})
            .when(catalogSpy).lockProfessional(BUSINESS,PROFESSIONAL);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"review-customer"));
        try{
            var future=new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Integer>>();
            new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s->{
                catalog.lockProfessional(BUSINESS,PROFESSIONAL);
                future.set(pool.submit(()->{try{
                    if(move)portal.move(secret,id,new Move(at("10:00"),0));else portal.cancel(secret,id,new Change(0));
                    return 200;
                }catch(br.com.luizcode.agenda.shared.BusinessException ex){return ex.status().value();}}));
                try{assertThat(attempted.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}catch(InterruptedException ex){throw new AssertionError(ex);}
                doReturn(at("08:00").toInstant().plusSeconds(1)).when(testClock).instant();return true;
            });
            assertThat(future.get().get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);unchanged(id);
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
    }

}
