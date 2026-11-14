package br.com.luizcode.agenda;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.*;
import br.com.luizcode.agenda.customer.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class AppointmentOutcomeTest extends ApiTestBase {
    @Autowired AppointmentOutcomeService outcomes;
    @Autowired CustomerPortalService portal;
    @Autowired ScheduleRepository appointments;
    @MockitoSpyBean AppointmentHistory history;
    @MockitoSpyBean(name="deterministicClock") Clock testClock;
    @BeforeEach void setup(){doReturn(Instant.parse("2030-06-03T10:00:00Z")).when(testClock).instant();}
    @AfterEach void restore(){reset(history,testClock);}
    UUID book(){var r=schedule.book(catalog.business(BUSINESS),request("09:00")).receipt();return jdbc.queryForObject("select id from appointment where reference=?",UUID.class,r.reference());}
    UUID ended(){var id=book();doReturn(at("09:45").toInstant()).when(testClock).instant();return id;}
    Change change(Result status,int version,String reason){return new Change(status,version,UUID.randomUUID(),reason);}
    String path(UUID id){return "/api/admin/appointments/"+id;}
    @ParameterizedTest @ValueSource(strings={"COMPLETED","NO_SHOW"})
    void recordsTheOutcomeAtTheExactEndAndKeepsTheContract(String state){
        var id=ended();var before=appointments.appointment(BUSINESS,id);
        var result=outcomes.change(owner(),id,change(Result.valueOf(state),0,null));
        assertThat(result.status()).isEqualTo(state);assertThat(result.version()).isEqualTo(1);
        assertThat(result.priceCents()).isEqualTo(before.priceCents());assertThat(result.startsAt()).isEqualTo(before.startsAt());assertThat(result.bookingPolicy()).isEqualTo(before.bookingPolicy());
        var rows=outcomes.history(BUSINESS,id,null).events();assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().actorName()).isEqualTo("Marina");assertThat(rows.getFirst().fromStatus()).isEqualTo("BOOKED");assertThat(rows.getFirst().toStatus()).isEqualTo(state);
    }
    @Test void refusesTheInstantBeforeTheEnd(){
        var id=ended();doReturn(at("09:45").toInstant().minusNanos(1)).when(testClock).instant();
        assertThatThrownBy(()->outcomes.change(owner(),id,change(Result.COMPLETED,0,null))).hasMessageContaining("término");
        assertThat(appointments.appointment(BUSINESS,id).version()).isZero();assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(1);
    }
    @Test void cancelledReservationsCannotReceiveAnOutcome(){
        var id=book();schedule.cancel(catalog.business(BUSINESS),id,0);doReturn(at("10:00").toInstant()).when(testClock).instant();
        assertThatThrownBy(()->outcomes.change(owner(),id,change(Result.NO_SHOW,1,null))).hasMessageContaining("cancelada");
    }
    @Test void correctionRequiresAReasonAndPreservesBothEvents(){
        var id=ended();outcomes.change(owner(),id,change(Result.NO_SHOW,0,null));
        for(var reason:List.of("","    ","abcd"))assertThatThrownBy(()->outcomes.change(owner(),id,change(Result.COMPLETED,1,reason))).hasMessageContaining("5 caracteres");
        outcomes.change(owner(),id,change(Result.COMPLETED,1,"  Cliente compareceu; classificação corrigida.  "));
        var rows=outcomes.history(BUSINESS,id,null).events();assertThat(rows).hasSize(3);
        assertThat(rows.getFirst().action()).isEqualTo("CORRECTION");assertThat(rows.getFirst().reason()).isEqualTo("Cliente compareceu; classificação corrigida.");
        assertThat(rows.get(1).toStatus()).isEqualTo("NO_SHOW");
    }
    @Test void staleVersionAndSameResultDoNotCreateAnotherEvent(){
        var id=ended();outcomes.change(owner(),id,change(Result.COMPLETED,0,null));
        assertThatThrownBy(()->outcomes.change(owner(),id,change(Result.NO_SHOW,0,"Correção válida"))).isInstanceOf(BusinessException.class).hasMessageContaining("outra janela");
        assertThatThrownBy(()->outcomes.change(owner(),id,change(Result.COMPLETED,1,null))).hasMessageContaining("já foi registrado");
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(2);
    }
    @Test void retriesAreSafeEvenAfterACorrectionButKeyReuseWithOtherDataIsRefused(){
        var id=ended();var command=change(Result.NO_SHOW,0,"Teste de presença");
        outcomes.change(owner(),id,command);outcomes.change(owner(),id,command);
        outcomes.change(owner(),id,change(Result.COMPLETED,1,"Cliente compareceu."));
        assertThat(outcomes.change(owner(),id,command).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(()->outcomes.change(owner(),id,new Change(Result.COMPLETED,0,command.requestKey(),command.reason()))).hasMessageContaining("outros dados");
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(3);
    }
    @Test void failureWritingHistoryRollsBackTheOutcomeAndTheEvent(){
        var id=ended();
        doAnswer(i->{i.callRealMethod();throw new IllegalStateException("Falha simulada");}).when(history).append(any(),any(),eq("OUTCOME"),any(),anyString(),any(),any(),any());
        assertThatThrownBy(()->outcomes.change(owner(),id,change(Result.COMPLETED,0,null))).hasMessageContaining("Falha simulada");
        assertThat(appointments.appointment(BUSINESS,id).status()).isEqualTo("BOOKED");assertThat(appointments.appointment(BUSINESS,id).version()).isZero();
        assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(1);
    }
    @Test void failedCreationHistoryRollsBackTheBooking(){
        doThrow(new IllegalStateException("Falha simulada")).when(history).append(isNull(),any(),eq("CREATED"),any(),anyString(),any(),isNull(),isNull());
        assertThatThrownBy(this::book).hasMessageContaining("Falha simulada");assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isZero();
    }
    @Test void historyIncludesReschedulingAndCancellationWithoutDuplicatingRetries(){
        var id=book();schedule.rescheduleOwner(catalog.business(BUSINESS),id,new ScheduleModels.RescheduleRequest(at("10:00"),0),owner());
        schedule.cancelOwner(catalog.business(BUSINESS),id,1,owner());schedule.cancelOwner(catalog.business(BUSINESS),id,1,owner());
        var rows=outcomes.history(BUSINESS,id,null).events();assertThat(rows).extracting(AppointmentHistory.Entry::action).containsExactly("CANCELLED","RESCHEDULED","CREATED");
        assertThat(rows.get(1).fromStart()).isEqualTo(at("09:00").toInstant());assertThat(rows.get(1).toStart()).isEqualTo(at("10:00").toInstant());
    }
    @Test void historyIsPaginatedWithoutMissingOrRepeatingEvents(){
        var id=ended();for(int i=0;i<55;i++)outcomes.change(owner(),id,change(i%2==0?Result.COMPLETED:Result.NO_SHOW,i,"Correção de teste "+i));
        var first=outcomes.history(BUSINESS,id,null);assertThat(first.events()).hasSize(50);assertThat(first.nextBeforeVersion()).isNotNull();
        var next=outcomes.history(BUSINESS,id,first.nextBeforeVersion());assertThat(next.events()).hasSize(6);assertThat(next.nextBeforeVersion()).isNull();
        var ids=new HashSet<UUID>();first.events().forEach(e->assertThat(ids.add(e.id())).isTrue());next.events().forEach(e->assertThat(ids.add(e.id())).isTrue());
    }
    @Test void legacyReservationIsNotAutomaticallyClassifiedOrGivenInventedHistory(){
        var id=ended();jdbc.update("delete from appointment_history where appointment_id=?",id);
        assertThat(appointments.appointment(BUSINESS,id).status()).isEqualTo("BOOKED");assertThat(outcomes.history(BUSINESS,id,null).events()).isEmpty();
        outcomes.change(owner(),id,change(Result.COMPLETED,0,null));assertThat(outcomes.history(BUSINESS,id,null).events()).hasSize(1);
    }
    @Test void ownerAuthorizationCsrfAndTenantScopeAreRequired() throws Exception{
        var id=ended();var command=change(Result.COMPLETED,0,null);
        mvc.perform(get(path(id)+"/history")).andExpect(status().isUnauthorized());
        mvc.perform(post(path(id)+"/outcome").with(user(owner())).contentType("application/json").content(json(command))).andExpect(status().isForbidden());
        var foreign=new br.com.luizcode.agenda.identity.AgendaUser(id("estudio-aurora-owner"),OTHER_BUSINESS,"Rafael","rafael@demo.agendaclara.dev","unused");
        mvc.perform(get(path(id)+"/history").with(user(foreign))).andExpect(status().isNotFound());
        mvc.perform(post(path(id)+"/outcome").with(user(foreign)).with(csrf()).contentType("application/json").content(json(command))).andExpect(status().isNotFound());
        mvc.perform(post(path(id)+"/outcome").with(user(owner())).with(csrf()).contentType("application/json").content(json(command))).andExpect(status().isOk());
        mvc.perform(get(path(id)+"/history?beforeVersion=-1").with(user(owner()))).andExpect(status().isBadRequest());
    }
    @Test void malformedRequestsDoNotChangeTheReservation() throws Exception{
        var id=ended();
        for(String body:List.of("{}","{\"status\":\"BOOKED\",\"version\":0,\"requestKey\":\""+UUID.randomUUID()+"\"}","{\"status\":\"COMPLETED\",\"version\":0.5,\"requestKey\":\""+UUID.randomUUID()+"\"}",json(new Change(Result.COMPLETED,0,UUID.randomUUID(),"x".repeat(501)))))
            mvc.perform(post(path(id)+"/outcome").with(user(owner())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertThat(appointments.appointment(BUSINESS,id).version()).isZero();
    }
    @Autowired CustomerKeys keys;
    @Test void customerSeesResultButCannotChangeItOrReadOwnerHistory() throws Exception{
        var id=ended();var secret=portal.open(keys.token(id,1),null).secret();outcomes.change(owner(),id,change(Result.NO_SHOW,0,null));
        var view=portal.read(secret,id);assertThat(view.status()).isEqualTo("NO_SHOW");assertThat(view.canCancel()).isFalse();assertThat(view.canReschedule()).isFalse();
        assertThatThrownBy(()->portal.move(secret,id,new CustomerPortalService.Move(at("11:00"),1))).isInstanceOf(BusinessException.class);
        var cookie=new jakarta.servlet.http.Cookie(CustomerPortalController.COOKIE,secret);
        mvc.perform(get(path(id)+"/history").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post(path(id)+"/outcome").cookie(cookie).with(csrf()).contentType("application/json").content(json(change(Result.COMPLETED,1,"Correção indevida")))).andExpect(status().isUnauthorized());
    }
}
