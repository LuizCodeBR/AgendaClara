package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.scheduling.DailySummaryService.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class DailySummaryTest extends ApiTestBase {
    @Autowired DailySummaryService summaries;
    @Autowired br.com.luizcode.agenda.team.TeamService team;
    @MockitoSpyBean br.com.luizcode.agenda.catalog.CatalogRepository catalogSpy;
    private record SavedContact(UUID id,String phone,int version){}
    private List<SavedContact> savedContacts;
    @BeforeEach void resetContact(){
        savedContacts=jdbc.query("select id,whatsapp_phone,version from professional",(r,n)->new SavedContact(r.getObject(1,UUID.class),r.getString(2),r.getInt(3)));
        jdbc.update("update professional set whatsapp_phone=''");
    }
    @AfterEach void restore(){
        reset(catalogSpy);
        for(var saved:savedContacts)jdbc.update("update professional set whatsapp_phone=?,version=? where id=?",saved.phone(),saved.version(),saved.id());
    }
    String path(UUID id){return "/api/admin/professionals/"+id;}
    UUID book(String time){var receipt=schedule.book(catalog.business(BUSINESS),request(time)).receipt();return jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference());}
    int version(){return jdbc.queryForObject("select version from professional where id=?",Integer.class,PROFESSIONAL);}
    @Test void timestampDescribesStartOfReadEvenWhenTheDatabaseIsSlow(){
        var start=Instant.parse("2030-06-03T10:00:00Z");var now=new java.util.concurrent.atomic.AtomicReference<>(start);
        var slowClock=mock(Clock.class);when(slowClock.instant()).thenAnswer(i->now.get());when(slowClock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
        doAnswer(i->{var value=i.callRealMethod();now.set(start.plusSeconds(360));return value;}).when(catalogSpy).business(BUSINESS);
        var service=new DailySummaryService(jdbc,catalogSpy,slowClock);
        assertThat(service.read(BUSINESS,PROFESSIONAL,DATE).generatedAt()).isEqualTo(start);
    }
    @Test void privateResponsesCannotBeCachedAndAUserWithoutOwnerRoleCannotReadOrWrite() throws Exception {
        mvc.perform(get(path(PROFESSIONAL)+"/daily-summary?date="+DATE).with(user(owner()))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get(path(PROFESSIONAL)+"/daily-summary?date="+DATE).with(user("client").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(put(path(PROFESSIONAL)+"/whatsapp-contact").with(user("client").roles("CUSTOMER")).with(csrf()).contentType("application/json").content(json(new Contact("5511000000000",version())))).andExpect(status().isForbidden());
    }
    @Test void contactRemovalAndTeamEditCannotBothOverwriteTheSameVersion() throws Exception {
        var before=team.list(BUSINESS).stream().filter(p->p.id().equals(PROFESSIONAL)).findFirst().orElseThrow();
        var saved=summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact("5511000000000",before.version()));
        var update=new br.com.luizcode.agenda.team.TeamModels.UpdateProfessional(before.name()+" rev",before.specialty(),before.active(),saved.version(),before.hours(),before.serviceIds());
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var removal=pool.submit(()->{ready.countDown();go.await(10,TimeUnit.SECONDS);try{summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact("",saved.version()));return 200;}catch(br.com.luizcode.agenda.shared.BusinessException e){return e.status().value();}});
            var edit=pool.submit(()->{ready.countDown();go.await(10,TimeUnit.SECONDS);try{team.update(BUSINESS,PROFESSIONAL,update);return 200;}catch(br.com.luizcode.agenda.shared.BusinessException e){return e.status().value();}});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();var removed=removal.get(15,TimeUnit.SECONDS);
            assertThat(List.of(removed,edit.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            var current=summaries.read(BUSINESS,PROFESSIONAL,DATE).professional();assertThat(current.version()).isEqualTo(saved.version()+1);
            assertThat(current.phone()).isEqualTo(removed==200?"":saved.phone());assertThat(current.name()).isEqualTo(removed==200?before.name():update.name());
        }finally{go.countDown();jdbc.update("update professional set name=? where id=?",before.name(),PROFESSIONAL);}
    }
    @Test void summaryIsSortedAndIncludesOnlyTheSelectedProfessionalsConfirmedAppointments(){
        book("11:00");var cancelled=book("10:00");schedule.cancelOwner(catalog.business(BUSINESS),cancelled,0,owner());var first=book("09:00");
        var completed=book("14:00");var missing=book("15:00");
        jdbc.update("update appointment set status='COMPLETED' where id=?",completed);jdbc.update("update appointment set status='NO_SHOW' where id=?",missing);
        var result=summaries.read(BUSINESS,PROFESSIONAL,DATE);
        assertThat(result.appointments()).hasSize(2).extracting(Item::startsAt).containsExactly(at("09:00").toInstant(),at("11:00").toInstant());
        assertThat(summaries.read(BUSINESS,SECOND_PROFESSIONAL,DATE).appointments()).isEmpty();
        assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE.plusDays(1)).appointments()).isEmpty();
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2030-06-03T10:00:00Z"));
        assertThat(result.zoneId()).isEqualTo("America/Sao_Paulo");
    }
    @Test void boundariesUseTheCompanyZoneAndExcludeTheNextDaysMidnight(){
        var a=book("09:00");var b=book("10:00");
        jdbc.update("update appointment set starts_at=?,ends_at=? where id=?",at("00:00"),at("00:30"),a);
        jdbc.update("update appointment set starts_at=?,ends_at=? where id=?",at("00:00").plusDays(1),at("00:30").plusDays(1),b);
        assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE).appointments()).extracting(Item::startsAt).containsExactly(at("00:00").toInstant());
        assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE.plusDays(1)).appointments()).extracting(Item::startsAt).containsExactly(at("00:00").plusDays(1).toInstant());
    }
    @Test void reschedulesAppearOnlyOnTheirNewDayAndInactiveHistoryRemainsReadable(){
        var id=book("09:00");schedule.rescheduleOwner(catalog.business(BUSINESS),id,new ScheduleModels.RescheduleRequest(at("10:00").plusDays(1),0),owner());
        assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE).appointments()).isEmpty();
        assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE.plusDays(1)).appointments()).hasSize(1);
        jdbc.update("update professional set active=false where id=?",PROFESSIONAL);
        try{assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE.plusDays(1)).appointments()).hasSize(1);}
        finally{jdbc.update("update professional set active=true where id=?",PROFESSIONAL);}
    }
    @Test void labelsCannotInjectNewLinesOrDirectionControls(){
        var id=book("09:00");jdbc.update("update appointment set customer_name=?,service_name=? where id=?","João\n09:00\u202e","Corte\r\nBarba",id);
        var item=summaries.read(BUSINESS,PROFESSIONAL,DATE).appointments().getFirst();
        assertThat(item.customerName()).isEqualTo("João 09:00");assertThat(item.serviceName()).isEqualTo("Corte Barba");
    }
    @Test void foreignProfessionalsAndUnboundedDatesAreRejected() throws Exception {
        mvc.perform(get(path(PROFESSIONAL)+"/daily-summary?date="+DATE)).andExpect(status().isUnauthorized());
        mvc.perform(get(path(OTHER_PROFESSIONAL)+"/daily-summary?date="+DATE).with(user(owner()))).andExpect(status().isNotFound());
        for(String date:List.of("not-a-date","2030-02-30","2020-01-01","2040-01-01"))
            mvc.perform(get(path(PROFESSIONAL)+"/daily-summary?date="+date).with(user(owner()))).andExpect(status().isBadRequest());
        mvc.perform(get(path(PROFESSIONAL)+"/daily-summary").with(user(owner()))).andExpect(status().isBadRequest());
    }
    @Test void contactIsNormalizedPrivateVersionedAndDoesNotChangeTheSchedule() throws Exception {
        var id=book("09:00");int v=version();
        var saved=summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact("+55 (11) 99999-1234",v));
        assertThat(saved.phone()).isEqualTo("5511999991234");assertThat(saved.version()).isEqualTo(v+1);
        assertThat(summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact(saved.phone(),saved.version())).version()).isEqualTo(saved.version());
        assertThat(jdbc.queryForObject("select version from appointment where id=?",Integer.class,id)).isZero();
        String summary=mvc.perform(get(path(PROFESSIONAL)+"/daily-summary?date="+DATE).with(user(owner()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(summary).contains("5511999991234").doesNotContain("customerPhone","customerAccessToken","reference","reason");
        mvc.perform(get("/api/public/"+SLUG+"/catalog")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("5511999991234"))));
        assertThat(summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact("",saved.version())).phone()).isEmpty();
    }
    @Test void savingContactRequiresOwnerCsrfAndTenantScope() throws Exception {
        var contact=json(new Contact("5511999991234",version()));
        mvc.perform(put(path(PROFESSIONAL)+"/whatsapp-contact").with(user(owner())).contentType("application/json").content(contact)).andExpect(status().isForbidden());
        mvc.perform(put(path(PROFESSIONAL)+"/whatsapp-contact").with(csrf()).contentType("application/json").content(contact)).andExpect(status().isUnauthorized());
        mvc.perform(put(path(OTHER_PROFESSIONAL)+"/whatsapp-contact").with(user(owner())).with(csrf()).contentType("application/json").content(contact)).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"0123456789","1234567","1234567890123456","55abc999999","https://evil.test","55+11999991234"})
    void invalidPhonesDoNotChangeTheRecord(String phone) throws Exception {
        int v=version();
        mvc.perform(put(path(PROFESSIONAL)+"/whatsapp-contact").with(user(owner())).with(csrf()).contentType("application/json").content(json(new Contact(phone,v)))).andExpect(status().isBadRequest());
        assertThat(version()).isEqualTo(v);
        assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE).professional().phone()).isEmpty();
    }
    @Test void oversizedDayIsRefusedInsteadOfSilentlyDroppingAppointments(){
        var id=book("09:00");var rows=new ArrayList<Object[]>();
        for(int i=0;i<200;i++){var key=UUID.randomUUID();rows.add(new Object[]{key,key.toString().substring(0,12),UUID.randomUUID(),id});}
        jdbc.batchUpdate("""
            insert into appointment(id,business_id,professional_id,service_id,service_name,price_cents,customer_name,customer_phone,starts_at,ends_at,status,reference,request_key,request_fingerprint,created_at,updated_at)
            select ?,business_id,professional_id,service_id,service_name,price_cents,customer_name,customer_phone,starts_at,ends_at,status,?,?,request_fingerprint,created_at,updated_at from appointment where id=?
            """,rows);
        assertThatThrownBy(()->summaries.read(BUSINESS,PROFESSIONAL,DATE)).hasMessageContaining("200 atendimentos");
    }
    @Test void changingContactDoesNotLetAnOldTeamEditorOverwriteTheProfile(){
        var current=jdbc.queryForObject("select version from professional where id=?",Integer.class,PROFESSIONAL);
        summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact("5511000000000",current));
        try{
            mvc.perform(put(path(PROFESSIONAL)).with(user(owner())).with(csrf()).contentType("application/json")
                .content("{\"name\":\"Nome antigo\",\"specialty\":\"Teste\",\"active\":true,\"version\":"+current+",\"hours\":[],\"serviceIds\":[]}"))
                .andExpect(status().isConflict());
        }catch(Exception e){throw new AssertionError(e);}
        assertThat(summaries.read(BUSINESS,PROFESSIONAL,DATE).professional().phone()).isEqualTo("5511000000000");
    }
    @Test void staleAndFractionalContactVersionsAreRejected() throws Exception {
        int v=version();summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact("5511999991234",v));
        mvc.perform(put(path(PROFESSIONAL)+"/whatsapp-contact").with(user(owner())).with(csrf()).contentType("application/json").content(json(new Contact("",v)))).andExpect(status().isConflict());
        mvc.perform(put(path(PROFESSIONAL)+"/whatsapp-contact").with(user(owner())).with(csrf()).contentType("application/json").content("{\"phone\":\"\",\"version\":0.5}")).andExpect(status().isBadRequest());
    }
    @Test void simultaneousContactChangesHaveOnlyOneWinner() throws Exception {
        int v=version();var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Integer>>();
            for(String phone:List.of("5511999991234","5511999995678"))futures.add(pool.submit(()->{
                ready.countDown();assertThat(go.await(10,TimeUnit.SECONDS)).isTrue();
                try{summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact(phone,v));return 200;}catch(br.com.luizcode.agenda.shared.BusinessException e){return e.status().value();}
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(version()).isEqualTo(v+1);
        }finally{go.countDown();}
    }
    @Test void summaryKeepsAConsistentSnapshotDuringCancellationAndContactChange() throws Exception {
        var id=book("09:00");int v=version();var snapshot=new CountDownLatch(1);var changed=new CountDownLatch(1);
        doAnswer(i->{var result=i.callRealMethod();if(Thread.currentThread().getName().equals("summary-reader")){snapshot.countDown();assertThat(changed.await(10,TimeUnit.SECONDS)).isTrue();}return result;}).when(catalogSpy).business(BUSINESS);
        try(var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"summary-reader"))){
            var future=pool.submit(()->summaries.read(BUSINESS,PROFESSIONAL,DATE));
            assertThat(snapshot.await(10,TimeUnit.SECONDS)).isTrue();
            schedule.cancelOwner(catalog.business(SLUG),id,0,owner());
            summaries.saveContact(BUSINESS,PROFESSIONAL,new Contact("5511999991234",v));changed.countDown();
            var old=future.get(15,TimeUnit.SECONDS);assertThat(old.appointments()).hasSize(1);assertThat(old.professional().phone()).isEmpty();
            var current=summaries.read(BUSINESS,PROFESSIONAL,DATE);assertThat(current.appointments()).isEmpty();assertThat(current.professional().phone()).isEqualTo("5511999991234");
        }finally{changed.countDown();}
    }
}
