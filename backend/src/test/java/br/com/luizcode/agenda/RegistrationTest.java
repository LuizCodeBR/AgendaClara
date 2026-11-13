package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.identity.*;
import br.com.luizcode.agenda.identity.RegistrationModels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties="app.protection.registration-per-minute=500")
class RegistrationTest extends ApiTestBase {
    static final String PASSWORD="uma frase de teste 2030";
    @Autowired PasswordEncoder passwords;
    @AfterEach void cleanAccounts() {
        for (UUID id : jdbc.query("select id from business where name like 'Cadastro teste%'",(rs,row)->rs.getObject(1,UUID.class))) {
            jdbc.update("delete from account_action where user_id in (select id from app_user where business_id=?)",id);
            for (String table : List.of("date_hours_window","date_hours","appointment","time_block","weekly_hours","professional","service_offering","app_user"))
                jdbc.update("delete from "+table+" where business_id=?",id);
            jdbc.update("delete from business where id=?",id);
        }
    }
    Map<String,Object> request() {
        var data=new HashMap<String,Object>();
        data.put("requestKey",UUID.randomUUID()); data.put("businessName","Cadastro teste Ágil");
        data.put("category","Consultoria"); data.put("address","Atendimento online");
        data.put("zoneId","America/Sao_Paulo"); data.put("ownerName","Ana Teste");
        data.put("email",UUID.randomUUID()+"@example.test"); data.put("password",PASSWORD);
        data.put("demoAcknowledged",true); return data;
    }
    MvcResult register(Map<String,Object> data,int status) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json(data)))
                .andExpect(status().is(status)).andReturn();
    }
    UUID company(MvcResult result) { return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).get("businessId").asText()); }

    @Test void createsOnlyPrivateCompanyAndOwnerWithNormalizedEmailAndHashedPassword() throws Exception {
        var data=request(); data.put("email","  ANA."+UUID.randomUUID()+"@EXAMPLE.TEST  ");
        var result=register(data,201); UUID id=company(result);
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(PASSWORD,"password","hash");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        String email=data.get("email").toString().strip().toLowerCase(Locale.ROOT);
        var row=jdbc.queryForMap("select * from app_user where business_id=?",id);
        assertThat(row.get("email")).isEqualTo(email);
        String hash=row.get("password_hash").toString();
        assertThat(hash).isNotEqualTo(PASSWORD); assertThat(passwords.matches(PASSWORD,hash)).isTrue();
        assertThat(jdbc.queryForObject("select public_booking_enabled from business where id=?",Boolean.class,id)).isFalse();
        for (String table:List.of("professional","service_offering","appointment","weekly_hours"))
            assertThat(jdbc.queryForObject("select count(*) from "+table+" where business_id=?",Integer.class,id)).isZero();
        String slug=mapper.readTree(result.getResponse().getContentAsByteArray()).get("slug").asText();
        assertThat(slug).matches("cadastro-teste-agil-[a-f0-9]{32}");
        mvc.perform(get("/api/public/"+slug+"/catalog")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/businesses")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(id.toString()))));
    }
    @Test void retryReturnsSameReceiptButChangedPasswordOrDataCannotReuseKey() throws Exception {
        var data=request(); var first=register(data,201);
        assertThat(register(data,200).getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(first.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        data.put("password",PASSWORD+"x"); register(data,409);
        data.put("password",PASSWORD); data.put("ownerName","Outra pessoa"); register(data,409);
        assertThat(jdbc.queryForObject("select count(*) from app_user where registration_key=?",Integer.class,data.get("requestKey"))).isEqualTo(1);
    }
    @Test void duplicateEmailRollsBackTheEntireCompany() throws Exception {
        var first=request(); register(first,201);
        int before=jdbc.queryForObject("select count(*) from business",Integer.class);
        var next=request(); next.put("email",first.get("email").toString().toUpperCase(Locale.ROOT));
        register(next,409);
        assertThat(jdbc.queryForObject("select count(*) from business",Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from app_user where registration_key=?",Integer.class,next.get("requestKey"))).isZero();
    }
    @Test void fourConcurrentRetriesCreateOneCompanyAndOneAccount() throws Exception {
        var data=request();
        var outcomes=race(4, () -> mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json(data))).andReturn());
        assertThat(outcomes).filteredOn(r->r.getResponse().getStatus()==201).hasSize(1);
        assertThat(outcomes).allSatisfy(r->assertThat(r.getResponse().getStatus()).isIn(200,201));
        assertThat(outcomes.stream().map(this::company).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from app_user where registration_key=?",Integer.class,data.get("requestKey"))).isEqualTo(1);
    }
    @Test void concurrentDifferentKeysForSameEmailLeaveNoOrphanCompany() throws Exception {
        var data=request(); var other=new HashMap<>(data); other.put("requestKey",UUID.randomUUID());
        int before=jdbc.queryForObject("select count(*) from business",Integer.class);
        var index=new java.util.concurrent.atomic.AtomicInteger();
        var outcomes=race(2,()->{
            var body=index.getAndIncrement()==0?data:other;
            return mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json(body))).andReturn().getResponse().getStatus();
        });
        assertThat(outcomes).containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("select count(*) from business",Integer.class)).isEqualTo(before+1);
    }
    @Test void realLoginRotatesSessionAndRestoresOnlyNewOwnerThenLogoutRevokesAccess() throws Exception {
        var data=request(); UUID id=company(register(data,201));
        var anonymous=(MockHttpSession)mvc.perform(get("/api/auth/csrf")).andReturn().getRequest().getSession();
        String previousId=anonymous.getId();
        var login=mvc.perform(post("/api/auth/login").session(anonymous).with(csrf()).with(req -> { req.setRequestedSessionId(previousId); req.setRequestedSessionIdValid(true); return req; })
                .param("username",data.get("email").toString()).param("password",PASSWORD)).andExpect(status().isNoContent()).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(previousId);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.business.id").value(id.toString()));
        mvc.perform(get("/api/admin/catalog").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.services").isEmpty()).andExpect(jsonPath("$.professionals").isEmpty());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/admin/business")).andExpect(status().isUnauthorized());
    }
    @Test void registrationRequiresCsrfAndRefusesAlreadyAuthenticatedOwner() throws Exception {
        var data=request();
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json(data))).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").with(user(owner())).with(csrf()).contentType("application/json").content(json(data))).andExpect(status().isConflict());
    }
    static Stream<Map.Entry<String,Object>> invalidInputs() {
        return Stream.of(Map.entry("password","curta"),Map.entry("password","á".repeat(37)),
                Map.entry("password","😀".repeat(14)),Map.entry("email","invalid"),
                Map.entry("email","marina@demo.agendaclara.dev"),Map.entry("zoneId","Europe/London"),
                Map.entry("businessName"," "),Map.entry("ownerName","A"),Map.entry("address"," "),
                Map.entry("category","x".repeat(81)),Map.entry("requestKey","not-uuid"));
    }
    @ParameterizedTest @MethodSource("invalidInputs")
    void rejectsInvalidInputsWithoutCreatingCompany(Map.Entry<String,Object> invalid) throws Exception {
        var data=request(); data.put(invalid.getKey(),invalid.getValue());
        int before=jdbc.queryForObject("select count(*) from business",Integer.class);
        register(data,400);
        assertThat(jdbc.queryForObject("select count(*) from business",Integer.class)).isEqualTo(before);
    }
    @Test void missingPasswordIsValidationErrorAndUnicodePasswordBoundaryIsNotTruncated() throws Exception {
        var data=request(); data.remove("password"); register(data,400);
        data.put("password","á".repeat(36)); var result=register(data,201);
        String hash=jdbc.queryForObject("select password_hash from app_user where business_id=?",String.class,company(result));
        assertThat(passwords.matches("á".repeat(36),hash)).isTrue();
    }
    @Test void publicDemoRequiresAcknowledgementAndRegistrationCanBeDisabled() {
        var dto=new RegisterRequest(UUID.randomUUID(),"Cadastro teste flags","Teste","Online","America/Sao_Paulo",
                "Ana Teste","flags@example.test",PASSWORD,false);
        assertThatThrownBy(()->new RegistrationService(jdbc,passwords,true,true).register(dto)).hasMessageContaining("dados fictícios");
        assertThatThrownBy(()->new RegistrationService(jdbc,passwords,false,false).register(dto)).hasMessageContaining("indisponível");
        assertThat(dto.toString()).doesNotContain(PASSWORD,"flags@example.test");
    }
    AgendaUser ownUser(UUID id) {
        return jdbc.query("select * from app_user where business_id=?", (rs,row)->new AgendaUser(rs.getObject("id",UUID.class),
                id,rs.getString("display_name"),rs.getString("email"),"unused"),id).getFirst();
    }
    UUID configure(UUID business,AgendaUser owner) throws Exception {
        var created=mvc.perform(post("/api/admin/professionals").with(user(owner)).with(csrf()).contentType("application/json")
                .content(json(Map.of("name","Pessoa própria","specialty","Consultoria")))).andExpect(status().isCreated()).andReturn();
        UUID person=UUID.fromString(mapper.readTree(created.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("id").asText());
        mvc.perform(put("/api/admin/professionals/"+person).with(user(owner)).with(csrf()).contentType("application/json")
                .content(json(Map.of("name","Pessoa própria","specialty","Consultoria","active",true,"version",0,
                        "hours",List.of(Map.of("weekday",2,"opensAt","09:00","closesAt","18:00")))))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/services").with(user(owner)).with(csrf()).contentType("application/json")
                .content(json(Map.of("name","Consulta própria","description","Atendimento teste","durationMinutes",30,"priceCents",5000))))
                .andExpect(status().isCreated());
        return person;
    }
    @Test void publicationRequiresSetupAndOptimisticVersionAndCanHideWithoutDeletingAppointments() throws Exception {
        UUID id=company(register(request(),201)); var own=ownUser(id);
        jdbc.update("update app_user set email_verified_at=? where business_id=?",at("08:00"),id);
        mvc.perform(get("/api/admin/business").with(user(own))).andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.ready").value(false)).andExpect(jsonPath("$.services").value(0));
        mvc.perform(put("/api/admin/business/publication").with(user(own)).with(csrf()).contentType("application/json")
                .content(json(Map.of("published",true,"version",0)))).andExpect(status().isBadRequest());
        UUID person=configure(id,own);
        mvc.perform(get("/api/admin/business").with(user(own))).andExpect(jsonPath("$.ready").value(true));
        mvc.perform(put("/api/admin/business/publication").with(user(own)).with(csrf()).contentType("application/json")
                .content(json(Map.of("published",true,"version",0)))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        String slug=catalog.business(id).slug();
        mvc.perform(get("/api/public/"+slug+"/catalog")).andExpect(status().isOk());
        UUID service=catalog.services(id).getFirst().id();
        var booking=schedule.book(catalog.business(id),new br.com.luizcode.agenda.scheduling.ScheduleModels.BookingRequest(
                UUID.randomUUID(),person,service,at("10:00"),"Cliente próprio","11999991234"));
        mvc.perform(put("/api/admin/business/publication").with(user(own)).with(csrf()).contentType("application/json")
                .content(json(Map.of("published",false,"version",0)))).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/business/publication").with(user(own)).with(csrf()).contentType("application/json")
                .content(json(Map.of("published",false,"version",1)))).andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false));
        mvc.perform(get("/api/public/"+slug+"/catalog")).andExpect(status().isNotFound());
        mvc.perform(post("/api/public/"+slug+"/bookings").with(csrf()).contentType("application/json")
                .content(json(new br.com.luizcode.agenda.scheduling.ScheduleModels.BookingRequest(UUID.randomUUID(),person,service,at("11:00"),"Outro cliente","11999991234"))))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from appointment where business_id=? and status='BOOKED'",Integer.class,id)).isEqualTo(1);
        mvc.perform(get("/api/admin/agenda?date="+DATE).with(user(own))).andExpect(jsonPath("$.appointments[0].reference").value(booking.receipt().reference()));
    }
    @Test void twoNewAccountsCannotReadOrMutateEachOthersTeamCalendarOrPublication() throws Exception {
        UUID a=company(register(request(),201)), b=company(register(request(),201)); var ownA=ownUser(a); var ownB=ownUser(b);
        UUID person=configure(a,ownA);
        mvc.perform(get("/api/admin/business?businessId="+a).with(user(ownB))).andExpect(jsonPath("$.business.id").value(b.toString()));
        mvc.perform(get("/api/admin/professionals").with(user(ownB))).andExpect(content().json("[]"));
        mvc.perform(put("/api/admin/professionals/"+person).with(user(ownB)).with(csrf()).contentType("application/json")
                .content(json(Map.of("name","Intruso","specialty","Teste","active",true,"version",1,"hours",List.of())))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/professionals/"+person+"/exceptions").with(user(ownB))
                .param("from",DATE.toString()).param("to",DATE.plusDays(1).toString())).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/business/publication").with(user(ownB)).with(csrf()).contentType("application/json")
                .content(json(Map.of("businessId",a,"published",true,"version",0)))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select public_booking_enabled from business where id=?",Boolean.class,a)).isFalse();
        UUID service=catalog.services(a).getFirst().id();
        var receipt=schedule.book(catalog.business(a),new br.com.luizcode.agenda.scheduling.ScheduleModels.BookingRequest(
                UUID.randomUUID(),person,service,at("10:00"),"Cliente exclusivo A","11999991234")).receipt();
        UUID appointment=jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference());
        mvc.perform(get("/api/admin/agenda?date="+DATE).with(user(ownB))).andExpect(jsonPath("$.appointments").isEmpty());
        mvc.perform(post("/api/admin/appointments/"+appointment+"/cancel").with(user(ownB)).with(csrf())).andExpect(status().isNotFound());
    }

    @Test void readinessCountsOnlyActiveProfessionalsAndUpcomingOpenExceptions() throws Exception {
        UUID id=company(register(request(),201)); var own=ownUser(id);
        jdbc.update("update app_user set email_verified_at=? where business_id=?",at("08:00"),id);
        UUID person=UUID.randomUUID(), exception=UUID.randomUUID();
        jdbc.update("insert into professional(id,business_id,name,specialty) values(?,?,?,?)",person,id,"Pessoa própria","Teste");
        jdbc.update("insert into service_offering(id,business_id,name,description,duration_minutes,price_cents) values(?,?,?,?,30,1000)",UUID.randomUUID(),id,"Teste","Teste");
        jdbc.update("insert into date_hours(id,business_id,professional_id,exception_date,reason) values(?,?,?,?,?)",exception,id,person,DATE,"Teste");
        mvc.perform(get("/api/admin/business").with(user(own))).andExpect(jsonPath("$.hasHours").value(false)).andExpect(jsonPath("$.ready").value(false));
        jdbc.update("insert into date_hours_window(id,business_id,exception_id,opens_at,closes_at) values(?,?,?,'09:00','12:00')",UUID.randomUUID(),id,exception);
        mvc.perform(get("/api/admin/business").with(user(own))).andExpect(jsonPath("$.hasHours").value(true)).andExpect(jsonPath("$.ready").value(true));
        jdbc.update("update professional set active=false where id=?",person);
        mvc.perform(get("/api/admin/business").with(user(own))).andExpect(jsonPath("$.hasHours").value(false));
        jdbc.update("update professional set active=true where id=?",person);
        jdbc.update("update date_hours set exception_date=? where id=?",DATE.minusDays(2),exception);
        mvc.perform(get("/api/admin/business").with(user(own))).andExpect(jsonPath("$.hasHours").value(false));
    }

    private <T> List<T> race(int count, Callable<T> action) throws Exception {
        try(var workers=Executors.newFixedThreadPool(count)) {
            var ready=new CountDownLatch(count); var start=new CountDownLatch(1); var futures=new ArrayList<Future<T>>();
            for(int i=0;i<count;i++) futures.add(workers.submit(()->{ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return action.call();}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results=new ArrayList<T>(); for(var future:futures) results.add(future.get(30,TimeUnit.SECONDS)); return results;
        }
    }
}
