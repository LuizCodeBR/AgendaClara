package br.com.luizcode.agenda;
import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
class AppointmentOutcomeConcurrencyTest extends ApiTestBase{
    @Autowired AppointmentOutcomeService outcomes;
    @MockitoSpyBean(name="deterministicClock") Clock clock;
    @AfterEach void resetClock(){reset(clock);}
    UUID ended(){var receipt=schedule.book(catalog.business(BUSINESS),request("09:00")).receipt();doReturn(at("09:45").toInstant()).when(clock).instant();return jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference());}
    List<Integer> race(UUID id,List<Change> commands) throws Exception{
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Integer>>();
            for(var command:commands)futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError();try{outcomes.change(owner(),id,command);return 200;}catch(BusinessException e){return e.status().value();}}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        }finally{start.countDown();}
    }
    @Test void distinctOutcomesCannotOverwriteEachOther()throws Exception{
        var id=ended();assertThat(race(id,List.of(new Change(Result.COMPLETED,0,UUID.randomUUID(),null),new Change(Result.NO_SHOW,0,UUID.randomUUID(),null)))).containsExactlyInAnyOrder(200,409);
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(2);
    }
    @Test void simultaneousRetriesCreateOneEvent()throws Exception{
        var id=ended();var command=new Change(Result.COMPLETED,0,UUID.randomUUID(),null);
        assertThat(race(id,List.of(command,command))).containsOnly(200);assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(2);
    }
    @Test void simultaneousCorrectionsKeepOneWinner()throws Exception{
        var id=ended();outcomes.change(owner(),id,new Change(Result.COMPLETED,0,UUID.randomUUID(),null));
        assertThat(race(id,List.of(new Change(Result.NO_SHOW,1,UUID.randomUUID(),"Correção A"),new Change(Result.NO_SHOW,1,UUID.randomUUID(),"Correção B")))).containsExactlyInAnyOrder(200,409);
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(3);
    }
}
