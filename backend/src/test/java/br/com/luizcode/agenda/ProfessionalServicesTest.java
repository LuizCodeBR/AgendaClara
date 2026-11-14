package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.team.TeamModels.*;
import br.com.luizcode.agenda.team.TeamRepository;
import br.com.luizcode.agenda.team.TeamService;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ProfessionalServicesTest extends ApiTestBase {
    @Autowired TeamRepository team;
    @Autowired TeamService service;
    @Autowired PlatformTransactionManager transactions;
    private final List<UUID> created = new ArrayList<>();
    private static final String TEAM="/api/admin/professionals";

    @AfterEach void restore() {
        jdbc.update("delete from appointment");
        for(UUID id:created){jdbc.update("delete from weekly_hours where professional_id=?",id);jdbc.update("delete from professional where id=?",id);}
        jdbc.update("update professional set version=0 where id=?",PROFESSIONAL);
    }
    UpdateProfessional update(List<UUID> ids,int version) {
        var p=team.find(BUSINESS,PROFESSIONAL);
        return new UpdateProfessional(p.name(),p.specialty(),p.active(),version,p.hours(),ids);
    }
    org.springframework.test.web.servlet.ResultActions save(UpdateProfessional body) throws Exception {
        return mvc.perform(put(TEAM+"/"+PROFESSIONAL).with(user(owner())).with(csrf()).contentType("application/json").content(json(body)));
    }
    ProfessionalConfiguration create() {
        var p=service.create(BUSINESS,new CreateProfessional("Pessoa de teste de serviços","Teste"));
        created.add(p.id());return p;
    }
    @Test void publicCatalogAndOwnerConfigurationExposeOnlyTheirAssignments() throws Exception {
        mvc.perform(get("/api/public/"+SLUG+"/catalog")).andExpect(status().isOk())
                .andExpect(jsonPath( "$.professionals[0].serviceIds",hasSize(3)));
        assertThat(team.find(BUSINESS,PROFESSIONAL).serviceIds()).contains(SERVICE).doesNotContain(OTHER_SERVICE);
        assertThat(catalog.professionals(BUSINESS).getFirst().serviceIds()).contains(SERVICE).doesNotContain(OTHER_SERVICE);
    }
    @Test void unassignedServiceCannotBeQueriedOrBookedEvenWithDirectApiRequest() throws Exception {
        save(update(List.of(),0)).andExpect(status().isOk()).andExpect(jsonPath("$.serviceIds",hasSize(0)));
        mvc.perform(get(AVAILABILITY).param("professionalId",PROFESSIONAL.toString()).param("serviceId",SERVICE.toString()).param("date",DATE.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail",containsString("não atende")));
        mvc.perform(post(BOOKINGS).with(csrf()).contentType("application/json").content(json(request("09:00")))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isZero();
    }
    @Test void newProfessionalNeedsExplicitServicesAndHours() throws Exception {
        var p=create();assertThat(p.serviceIds()).isEmpty();assertThat(p.hours()).isEmpty();
        var configured=service.update(BUSINESS,p.id(),new UpdateProfessional(p.name(),p.specialty(),true,0,List.of(),List.of(SERVICE)));
        assertThat(schedule.availability(catalog.business(BUSINESS),p.id(),SERVICE,DATE).slots()).isEmpty();
        service.update(BUSINESS,p.id(),new UpdateProfessional(p.name(),p.specialty(),true,configured.version(),
                List.of(new WeeklyWindow(2,LocalTime.of(9,0),LocalTime.NOON)),List.of(SERVICE)));
        assertThat(schedule.availability(catalog.business(BUSINESS),p.id(),SERVICE,DATE).slots()).isNotEmpty();
    }
    static Stream<List<UUID>> invalidIds() {
        return Stream.of(List.of(OTHER_SERVICE),List.of(UUID.randomUUID()),List.of(SERVICE,SERVICE),Arrays.asList((UUID)null));
    }
    @ParameterizedTest @MethodSource("invalidIds")
    void invalidAssignmentsDoNotPartiallySaveProfileHoursOrServices(List<UUID> ids) throws Exception {
        var before=team.find(BUSINESS,PROFESSIONAL);
        save(new UpdateProfessional("Nome que não deve salvar","Teste",true,0,List.of(),ids)).andExpect(status().isBadRequest());
        assertThat(team.find(BUSINESS,PROFESSIONAL)).isEqualTo(before);
    }
    @Test void serviceListIsRequiredAndBounded() throws Exception {
        save(update(null,0)).andExpect(status().isBadRequest());
        save(update(Collections.nCopies(501,SERVICE),0)).andExpect(status().isContentTooLarge());
        mvc.perform(put(TEAM+"/"+PROFESSIONAL).with(user(owner())).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Teste\",\"specialty\":\"Teste\",\"active\":true,\"version\":0,\"hours\":[]}")).andExpect(status().isBadRequest());
    }
    @Test void confirmedAndOngoingAppointmentsPreventRemovingTheirService() throws Exception {
        schedule.book(catalog.business(BUSINESS),request("09:00"));
        var before=team.find(BUSINESS,PROFESSIONAL);
        save(update(List.of(),0)).andExpect(status().isConflict()).andExpect(jsonPath("$.detail",containsString("serviço")));
        assertThat(team.find(BUSINESS,PROFESSIONAL)).isEqualTo(before);
        jdbc.update("update appointment set starts_at=?,ends_at=?",OffsetDateTime.parse("2030-06-03T09:45:00Z"),OffsetDateTime.parse("2030-06-03T10:15:00Z"));
        save(update(List.of(),0)).andExpect(status().isConflict());
    }
    @Test void cancelledAndEndedAppointmentsKeepTheirHistoryAfterUnlinking() throws Exception {
        var result=schedule.book(catalog.business(BUSINESS),request("09:00"));
        UUID id=jdbc.queryForObject("select id from appointment where reference=?",UUID.class,result.receipt().reference());
        schedule.cancel(catalog.business(BUSINESS),id);
        save(update(List.of(),0)).andExpect(status().isOk());
        save(update(List.of(SERVICE),1)).andExpect(status().isOk());
        jdbc.update("update appointment set status='BOOKED',starts_at=?,ends_at=? where id=?",at("09:00").minusDays(3),at("09:45").minusDays(3),id);
        save(update(List.of(),2)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select service_id from appointment where id=?",UUID.class,id)).isEqualTo(SERVICE);
    }
    @Test void staleTabCannotRestoreAnAssignmentRemovedByAnotherTab() throws Exception {
        save(update(List.of(),0)).andExpect(status().isOk());
        save(update(List.of(SERVICE),0)).andExpect(status().isConflict());
        assertThat(team.find(BUSINESS,PROFESSIONAL).serviceIds()).isEmpty();
    }
    @Test void databaseRejectsCrossBusinessAssignmentsAndDuplicates() {
        assertThatThrownBy(()->jdbc.update("insert into professional_service(business_id,professional_id,service_id) values (?,?,?)",BUSINESS,PROFESSIONAL,OTHER_SERVICE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("insert into professional_service(business_id,professional_id,service_id) values (?,?,?)",OTHER_BUSINESS,PROFESSIONAL,OTHER_SERVICE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("insert into professional_service(business_id,professional_id,service_id) values (?,?,?)",BUSINESS,PROFESSIONAL,SERVICE))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void publicationNeedsServicesAndHoursOnTheSameActiveProfessional() throws Exception {
        jdbc.update("delete from professional_service where business_id=?",BUSINESS);
        var p=create();
        service.update(BUSINESS,p.id(),new UpdateProfessional(p.name(),p.specialty(),true,0,List.of(),List.of(SERVICE)));
        mvc.perform(get("/api/admin/business").with(user(owner()))).andExpect(jsonPath("$.hasHours").value(true))
                .andExpect(jsonPath("$.hasBookableProfessional").value(false)).andExpect(jsonPath("$.ready").value(false));
        service.update(BUSINESS,p.id(),new UpdateProfessional(p.name(),p.specialty(),true,1,
                List.of(new WeeklyWindow(2,LocalTime.of(9,0),LocalTime.NOON)),List.of(SERVICE)));
        mvc.perform(get("/api/admin/business").with(user(owner()))).andExpect(jsonPath("$.hasBookableProfessional").value(true))
                .andExpect(jsonPath("$.ready").value(true));
    }
    @Test void replayStillReturnsTheSameBookingAfterCompatibleConfigurationChange() {
        var request=request("09:00");var before=schedule.book(catalog.business(BUSINESS),request);
        service.update(BUSINESS,PROFESSIONAL,update(List.of(SERVICE),0));
        var replay=schedule.book(catalog.business(BUSINESS),request);
        assertThat(replay.created()).isFalse();assertThat(replay.receipt()).isEqualTo(before.receipt());
    }
    @Test void removingFirstRejectsTheConcurrentBooking() throws Exception { competingChanges(false); }
    @Test void bookingFirstRejectsConcurrentRemoval() throws Exception { competingChanges(true); }
    void competingChanges(boolean bookingFirst) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var held=new CountDownLatch(1);var release=new CountDownLatch(1);var started=new CountDownLatch(1);
        var business=catalog.business(BUSINESS);
        try {
            var first=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                catalog.lockProfessional(BUSINESS,PROFESSIONAL);
                if(bookingFirst)schedule.book(business,request("09:00"));else service.update(BUSINESS,PROFESSIONAL,update(List.of(),0));
                held.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Second operation did not start");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
                return true;
            }));
            assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->{started.countDown();try{
                if(bookingFirst)service.update(BUSINESS,PROFESSIONAL,update(List.of(),0));else schedule.book(business,request("09:00"));
                return "unexpected success";
            }catch(BusinessException e){return e.getMessage();}});
            assertThat(started.await(10,TimeUnit.SECONDS)).isTrue();release.countDown();
            assertThat(first.get(15,TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15,TimeUnit.SECONDS)).contains(bookingFirst?"serviço":"não atende");
            assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isEqualTo(bookingFirst?1:0);
            assertThat(team.find(BUSINESS,PROFESSIONAL).serviceIds().contains(SERVICE)).isEqualTo(bookingFirst);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(15,TimeUnit.SECONDS)).isTrue();}
    }
}
