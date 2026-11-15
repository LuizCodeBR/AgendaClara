package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AppointmentOutcomeReviewTest extends ApiTestBase {
    @Autowired AppointmentOutcomeService outcomes;
    @MockitoSpyBean AppointmentHistory history;
    @Autowired ScheduleRepository appointments;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean(name="deterministicClock") Clock clock;
    @AfterEach void resetClock(){reset(clock,history);}
    UUID ended(){
        var receipt=schedule.book(catalog.business(BUSINESS),request("09:00")).receipt();
        doReturn(at("09:45").toInstant()).when(clock).instant();
        return jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference());
    }
    String path(UUID id){return "/api/admin/appointments/"+id;}
    @ParameterizedTest @ValueSource(strings={"0","1","\"0\"","\"1\"","true"})
    void onlyNamedOutcomesAreAccepted(String status) throws Exception {
        var id=ended();
        mvc.perform(post(path(id)+"/outcome").with(user(owner())).with(csrf()).contentType("application/json")
            .content("{\"status\":"+status+",\"version\":0,\"requestKey\":\""+UUID.randomUUID()+"\"}"))
            .andExpect(status().isBadRequest());
        assertThat(appointments.appointment(BUSINESS,id).status()).isEqualTo("BOOKED");
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(1);
    }
    @Test void zeroCharacterInReasonIsRejectedBeforeReachingTheDatabase() throws Exception {
        var id=ended();
        mvc.perform(post(path(id)+"/outcome").with(user(owner())).with(csrf()).contentType("application/json")
            .content(json(new Change(Result.COMPLETED,0,UUID.randomUUID(),"Texto"+(char)0+"inválido"))))
            .andExpect(status().isBadRequest());
        assertThat(appointments.appointment(BUSINESS,id).version()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETED","NO_SHOW"})
    void clockRollbackCannotTurnAnOutcomeIntoACancellation(String state){
        var id=ended();outcomes.change(owner(),id,new Change(Result.valueOf(state),0,UUID.randomUUID(),"Resultado correto"));
        doReturn(at("08:00").toInstant()).when(clock).instant();
        assertThatThrownBy(()->schedule.cancelOwner(catalog.business(BUSINESS),id,1,owner()))
            .hasMessageContaining("confirmados");
        assertThat(appointments.appointment(BUSINESS,id).status()).isEqualTo(state);
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(2);
    }
    @Test void failedCorrectionRollsBackTheResultAndKeepsItsKeyReusable(){
        var id=ended();outcomes.change(owner(),id,new Change(Result.NO_SHOW,0,UUID.randomUUID(),null));
        var command=new Change(Result.COMPLETED,1,UUID.randomUUID(),"Cliente compareceu");
        doAnswer(i->{i.callRealMethod();throw new IllegalStateException("Falha simulada de correção");})
            .when(history).append(any(),any(),eq("CORRECTION"),any(),anyString(),any(),any(),any());
        assertThatThrownBy(()->outcomes.change(owner(),id,command)).hasMessageContaining("Falha simulada");
        assertThat(appointments.appointment(BUSINESS,id).status()).isEqualTo("NO_SHOW");
        assertThat(appointments.appointment(BUSINESS,id).version()).isEqualTo(1);
        assertThat(history.attempt(BUSINESS,command.requestKey())).isEmpty();
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(2);
        reset(history);outcomes.change(owner(),id,command);
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(3);
    }
    @Test void insertingANewerCorrectionDoesNotRepeatOrSkipOlderPages(){
        var id=ended();
        for(int i=0;i<55;i++)outcomes.change(owner(),id,new Change(i%2==0?Result.COMPLETED:Result.NO_SHOW,i,UUID.randomUUID(),"Correção para paginação"));
        var first=outcomes.history(BUSINESS,id,null);
        outcomes.change(owner(),id,new Change(Result.NO_SHOW,55,UUID.randomUUID(),"Correção durante consulta"));
        var second=outcomes.history(BUSINESS,id,first.nextBeforeVersion());
        var versions=new ArrayList<Integer>();first.events().forEach(e->versions.add(e.version()));second.events().forEach(e->versions.add(e.version()));
        assertThat(versions).hasSize(56).doesNotHaveDuplicates().doesNotContain(56).contains(0,55);
        assertThat(outcomes.history(BUSINESS,id,null).events().getFirst().version()).isEqualTo(56);
    }
    @Test void professionalLockRequiresAWriteTransaction(){
        assertThatThrownBy(()->catalog.lockProfessional(BUSINESS,PROFESSIONAL)).isInstanceOf(IllegalStateException.class);
        var tx=new TransactionTemplate(transactions);tx.setReadOnly(true);
        assertThatThrownBy(()->tx.execute(s->{catalog.lockProfessional(BUSINESS,PROFESSIONAL);return true;})).isInstanceOf(IllegalStateException.class);
    }
    @Test void historyCannotBeAppendedOutsideAWriteTransaction(){
        var id=ended();var appointment=appointments.appointment(BUSINESS,id);
        jdbc.update("delete from appointment_history where appointment_id=?",id);
        // A missing outer transaction must fail before an event can commit on its own.
        Runnable append=()->history.append(appointment,appointment,"OUTCOME",AppointmentHistory.Actor.owner(owner()),"",clock.instant(),UUID.randomUUID(),0);
        assertThatThrownBy(append::run).isInstanceOf(IllegalStateException.class);
        var tx=new TransactionTemplate(transactions);tx.setReadOnly(true);
        assertThatThrownBy(()->tx.execute(s->{append.run();return true;})).isInstanceOf(IllegalStateException.class);
    }
}
