package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.customer.CustomerPortalService;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AppointmentOutcomeConcurrencyReviewTest extends ApiTestBase {
    @Autowired AppointmentOutcomeService outcomes;
    @Autowired CustomerPortalService portal;
    @Autowired ScheduleRepository appointments;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AppointmentHistory history;
    @MockitoSpyBean CatalogRepository catalogSpy;
    @MockitoSpyBean(name="deterministicClock") Clock clock;
    @AfterEach void restore(){reset(history,catalogSpy,clock);}
    UUID book(UUID professional){
        var r=request("09:00");
        var receipt=schedule.book(catalog.business(BUSINESS),new ScheduleModels.BookingRequest(r.requestKey(),professional,r.serviceId(),r.startsAt(),r.customerName(),r.customerPhone())).receipt();
        return jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference());
    }
    Change command(int version){return new Change(Result.COMPLETED,version,UUID.randomUUID(),null);}
    List<Integer> race(Callable<Integer> first,Callable<Integer> second) throws Exception {
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Integer>>();
            for(var action:List.of(first,second))futures.add(pool.submit(()->{ready.countDown();assertThat(go.await(10,TimeUnit.SECONDS)).isTrue();return action.call();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        }finally{go.countDown();}
    }
    int change(UUID id,Change command){
        try{outcomes.change(owner(),id,command);return 200;}catch(BusinessException e){return e.status().value();}
    }
    @Test void aKeyCollisionAcrossProfessionalsRollsBackTheLosingOutcome() throws Exception {
        var first=book(PROFESSIONAL);var second=book(SECOND_PROFESSIONAL);doReturn(at("10:00").toInstant()).when(clock).instant();
        var command=command(0);var reads=new CountDownLatch(2);
        // Both transactions must observe the absent key before either inserts it.
        doAnswer(i->{var result=i.callRealMethod();reads.countDown();assertThat(reads.await(10,TimeUnit.SECONDS)).isTrue();return result;})
            .when(history).attempt(BUSINESS,command.requestKey());
        Callable<Integer> a=()->mvc.perform(post("/api/admin/appointments/"+first+"/outcome").with(user(owner())).with(csrf()).contentType("application/json").content(json(command))).andReturn().getResponse().getStatus();
        Callable<Integer> b=()->mvc.perform(post("/api/admin/appointments/"+second+"/outcome").with(user(owner())).with(csrf()).contentType("application/json").content(json(command))).andReturn().getResponse().getStatus();
        assertThat(race(a,b)).containsExactlyInAnyOrder(200,409);
        var appointmentsNow=List.of(appointments.appointment(BUSINESS,first),appointments.appointment(BUSINESS,second));
        assertThat(appointmentsNow).extracting(ScheduleRepository.Appointment::status).containsExactlyInAnyOrder("BOOKED","COMPLETED");
        assertThat(appointmentsNow).extracting(ScheduleRepository.Appointment::version).containsExactlyInAnyOrder(0,1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_history where request_key=?",Integer.class,command.requestKey())).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void privateAccessChangesAndOutcomesShareTheVersionLock(boolean rotate) throws Exception {
        var id=book(PROFESSIONAL);doReturn(at("09:45").toInstant()).when(clock).instant();
        var results=race(()->change(id,command(0)),()->{
            try{
                if(rotate)portal.issue(BUSINESS,id,new CustomerPortalService.Issue(UUID.randomUUID(),0));
                else portal.revoke(BUSINESS,id,new CustomerPortalService.Change(0));
                return 200;
            }catch(BusinessException e){return e.status().value();}
        });
        assertThat(results).containsExactlyInAnyOrder(200,409);
        var current=appointments.appointment(BUSINESS,id);assertThat(current.version()).isEqualTo(1);
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(current.status().equals("COMPLETED")?2:1);
    }
    @ParameterizedTest @ValueSource(ints={0,1})
    void waitingOutcomeRechecksBothVersionAndRescheduledEnd(int guessedVersion) throws Exception {
        var id=book(PROFESSIONAL);doReturn(at("08:30").toInstant()).when(clock).instant();
        var attempted=new CountDownLatch(1);
        doAnswer(i->{if(Thread.currentThread().getName().equals("waiting-outcome"))attempted.countDown();return i.callRealMethod();})
            .when(catalogSpy).lockProfessional(BUSINESS,PROFESSIONAL);
        var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"waiting-outcome"));
        try{
            var future=new AtomicReference<Future<Integer>>();
            new TransactionTemplate(transactions).execute(s->{
                catalog.lockProfessional(BUSINESS,PROFESSIONAL);
                future.set(pool.submit(()->change(id,command(guessedVersion))));
                try{assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new AssertionError(e);}
                schedule.rescheduleOwner(catalog.business(BUSINESS),id,new ScheduleModels.RescheduleRequest(at("11:00"),0),owner());
                doReturn(at("09:45").toInstant()).when(clock).instant();return true;
            });
            assertThat(future.get().get(15,TimeUnit.SECONDS)).isEqualTo(409);
            var current=appointments.appointment(BUSINESS,id);assertThat(current.status()).isEqualTo("BOOKED");
            assertThat(current.startsAt()).isEqualTo(at("11:00").toInstant());
            assertThat(outcomes.history(BUSINESS,id,null).events()).extracting(AppointmentHistory.Entry::action).containsExactly("RESCHEDULED","CREATED");
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void uncommittedResultAndHistoryAreNotExposed() throws Exception {
        var id=book(PROFESSIONAL);doReturn(at("09:45").toInstant()).when(clock).instant();
        try(var pool=Executors.newSingleThreadExecutor()){
            new TransactionTemplate(transactions).execute(s->{
                outcomes.change(owner(),id,command(0));
                try{
                    assertThat(pool.submit(()->outcomes.history(BUSINESS,id,null).events().size()).get(10,TimeUnit.SECONDS)).isEqualTo(1);
                    assertThat(pool.submit(()->appointments.appointment(BUSINESS,id).status()).get(10,TimeUnit.SECONDS)).isEqualTo("BOOKED");
                }catch(Exception e){throw new AssertionError(e);}
                return true;
            });
        }
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(2);
        assertThat(appointments.appointment(BUSINESS,id).status()).isEqualTo("COMPLETED");
    }
}
