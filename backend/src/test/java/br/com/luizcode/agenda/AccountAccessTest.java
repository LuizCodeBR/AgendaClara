package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.identity.*;
import br.com.luizcode.agenda.identity.AccessModels.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties="app.protection.registration-per-minute=500")
class AccountAccessTest extends ApiTestBase {
    static final String PASSWORD="senha original de teste 2030", NEW_PASSWORD="nova frase exclusiva de teste 2030";
    record Account(UUID id,UUID businessId,String email,Cookie cookie) {}
    @Autowired PasswordEncoder passwords;
    @Autowired MailDispatcher mails;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @AfterEach void cleanup() {
        for(UUID id:jdbc.query("select id from business where name='Access teste'",(rs,row)->rs.getObject(1,UUID.class))) {
            jdbc.update("delete from mail_outbox where business_id=?",id);
            jdbc.update("delete from account_action where user_id in(select id from app_user where business_id=?)",id);
            for(String table:List.of("date_hours_window","date_hours","appointment","time_block","weekly_hours","professional","service_offering","app_user"))
                jdbc.update("delete from "+table+" where business_id=?",id);
            jdbc.update("delete from business where id=?",id);
        }
    }
    Account account() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        var result=mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json(Map.of(
                "requestKey",UUID.randomUUID(),"businessName","Access teste","category","Teste","address","Online","zoneId","America/Sao_Paulo",
                "ownerName","Ana Teste","email",email,"password",PASSWORD,"demoAcknowledged",true)))).andExpect(status().isCreated()).andReturn();
        UUID business=UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).get("businessId").asText());
        String header=result.getResponse().getHeaders("Set-Cookie").stream().filter(value->value.startsWith(AccountAccessController.COOKIE+"=")).findFirst().orElseThrow();
        Cookie cookie=new Cookie(AccountAccessController.COOKIE,header.substring(header.indexOf('=')+1,header.indexOf(';')));
        assertThat(header).contains("HttpOnly","SameSite=Strict","Path=/api/auth","Max-Age=3600");
        return new Account(jdbc.queryForObject("select id from app_user where business_id=?",UUID.class,business),business,email,cookie);
    }
    String token(Account account,String purpose) throws Exception {
        var result=mvc.perform(get("/api/auth/demo-mailbox").cookie(account.cookie())).andExpect(status().isOk()).andReturn();
        for(var item:mapper.readTree(result.getResponse().getContentAsByteArray())) if(item.get("purpose").asText().equals(purpose)) {
            String link=item.get("actionUrl").asText();
            assertThat(link).startsWith("http://localhost:8080/#/");
            return link.substring(link.indexOf("token=")+6);
        }
        throw new AssertionError("No test message: "+purpose);
    }
    void recovery(Account account) throws Exception {
        mvc.perform(post("/api/auth/password/request").cookie(account.cookie()).with(csrf()).contentType("application/json")
                .content(json(Map.of("email","  "+account.email().toUpperCase(Locale.ROOT)+"  ")))).andExpect(status().isAccepted());
    }
    MvcResult reset(String token,String password,int expected) throws Exception {
        return mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType("application/json")
                .content(json(Map.of("token",token,"password",password)))).andExpect(status().is(expected)).andReturn();
    }
    MockHttpSession login(Account account,String password) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("username",account.email()).param("password",password))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }
    @Test void newAccountsNeedVerificationToPublishAndConfirmationIsExplicitAndSingleUse() throws Exception {
        Account account=account(); var user=new AgendaUser(account.id(),account.businessId(),"Ana",account.email(),"unused");
        UUID person=UUID.randomUUID();
        jdbc.update("insert into professional(id,business_id,name,specialty) values(?,?,?,?)",person,account.businessId(),"Ana","Teste");
        jdbc.update("insert into weekly_hours(id,business_id,professional_id,weekday,opens_at,closes_at) values(?,?,?,2,'09:00','12:00')",UUID.randomUUID(),account.businessId(),person);
        jdbc.update("insert into service_offering(id,business_id,name,description,duration_minutes,price_cents) values(?,?,?,?,30,1000)",UUID.randomUUID(),account.businessId(),"Teste","Teste");
        String code=token(account,"VERIFY");
        assertThat(code).matches("[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("select token_digest from account_action where user_id=?",String.class,account.id())).hasSize(64).isNotEqualTo(code);
        mvc.perform(get("/api/admin/business").with(user(user))).andExpect(jsonPath("$.verificationRequired").value(true)).andExpect(jsonPath("$.ready").value(false));
        mvc.perform(put("/api/admin/business/publication").with(user(user)).with(csrf()).contentType("application/json")
                .content("{\"published\":true,\"version\":0}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Confirme")));
        mvc.perform(get("/api/auth/email/confirm").param("token",code)).andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject("select email_verified_at from app_user where id=?",OffsetDateTime.class,account.id())).isNull();
        mvc.perform(post("/api/auth/email/confirm").with(csrf()).contentType("application/json").content(json(Map.of("token",code)))).andExpect(status().isOk());
        mvc.perform(post("/api/auth/email/confirm").with(csrf()).contentType("application/json").content(json(Map.of("token",code)))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/business").with(user(user))).andExpect(jsonPath("$.emailVerified").value(true)).andExpect(jsonPath("$.ready").value(true));
        mvc.perform(put("/api/admin/business/publication").with(user(user)).with(csrf()).contentType("application/json")
                .content("{\"published\":true,\"version\":0}")).andExpect(status().isOk());
    }
    @Test void resetReplacesHashConfirmsEmailInvalidatesOtherTokensAndRevokesBothOldSessions() throws Exception {
        Account account=account(); String verification=token(account,"VERIFY");
        MockHttpSession first=login(account,PASSWORD), second=login(account,PASSWORD);
        recovery(account); String code=token(account,"RESET");
        reset(code,NEW_PASSWORD,200);
        assertThat(passwords.matches(NEW_PASSWORD,jdbc.queryForObject("select password_hash from app_user where id=?",String.class,account.id()))).isTrue();
        assertThat(jdbc.queryForObject("select credentials_version from app_user where id=?",Integer.class,account.id())).isEqualTo(1);
        mvc.perform(get("/api/auth/me").session(first)).andExpect(status().isUnauthorized()); assertThat(first.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").session(second)).andExpect(status().isUnauthorized()); assertThat(second.isInvalid()).isTrue();
        mvc.perform(post("/api/auth/login").with(csrf()).param("username",account.email()).param("password",PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(login(account,NEW_PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(account.email()));
        reset(code,NEW_PASSWORD,400);
        mvc.perform(post("/api/auth/email/confirm").with(csrf()).contentType("application/json").content(json(Map.of("token",verification)))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/demo-mailbox").cookie(account.cookie())).andExpect(content().string(org.hamcrest.Matchers.containsString("Sua senha da Agenda Clara foi alterada")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(NEW_PASSWORD))));
    }
    @Test void wrongPurposeExpiredAndMalformedLinksCannotChangePasswordOrVerify() throws Exception {
        Account account=account(); String verification=token(account,"VERIFY");
        reset(verification,NEW_PASSWORD,400);
        recovery(account); String reset=token(account,"RESET");
        mvc.perform(post("/api/auth/email/confirm").with(csrf()).contentType("application/json").content(json(Map.of("token",reset)))).andExpect(status().isBadRequest());
        jdbc.update("update account_action set expires_at=? where user_id=?",at("06:59").minusDays(1),account.id());
        reset(reset,NEW_PASSWORD,400); reset("bad",NEW_PASSWORD,400);
        mvc.perform(get("/api/auth/me").session(login(account,PASSWORD))).andExpect(status().isOk());
    }
    @Test void weakOrOversizePasswordDoesNotConsumeAValidLink() throws Exception {
        Account account=account(); recovery(account); String code=token(account,"RESET");
        reset(code,"curta",400); reset(code,"á".repeat(37),400); reset(code,"😀".repeat(14),400);
        reset(code,"á".repeat(36),200); login(account,"á".repeat(36));
    }
    @Test void genericRequestResponseDoesNotRevealAccountAndCannotExposeAnotherMailbox() throws Exception {
        Account a=account(), b=account(); recovery(a);
        String expected=mvc.perform(post("/api/auth/password/request").cookie(a.cookie()).with(csrf()).contentType("application/json")
                .content(json(Map.of("email",a.email())))).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        for(String email:List.of("unknown@example.test",b.email(),"marina@demo.agendaclara.dev"))
            mvc.perform(post("/api/auth/password/request").cookie(a.cookie()).with(csrf()).contentType("application/json").content(json(Map.of("email",email))))
                    .andExpect(status().isAccepted()).andExpect(content().json(expected));
        assertThat(jdbc.queryForObject("select count(*) from account_action where user_id=? and purpose='RESET'",Integer.class,b.id())).isZero();
        mvc.perform(get("/api/auth/demo-mailbox")).andExpect(content().json("[]"));
        mvc.perform(get("/api/auth/demo-mailbox").cookie(new Cookie(AccountAccessController.COOKIE,"a".repeat(43)))).andExpect(content().json("[]"));
        mvc.perform(get("/api/auth/demo-mailbox").cookie(b.cookie())).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(a.email()))));
        mvc.perform(post("/api/auth/demo-mailbox/open").with(csrf())).andExpect(status().isUnauthorized());
    }
    @Test void resendCooldownKeepsOriginalLinkAndLaterResendInvalidatesIt() throws Exception {
        Account account=account(); String original=token(account,"VERIFY");
        mvc.perform(post("/api/auth/email/request").cookie(account.cookie()).with(csrf()).contentType("application/json").content(json(Map.of("email",account.email())))).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("select count(*) from account_action where user_id=?",Integer.class,account.id())).isEqualTo(1);
        jdbc.update("update account_action set created_at=? where user_id=?",at("06:58").minusDays(1),account.id());
        mvc.perform(post("/api/auth/email/request").cookie(account.cookie()).with(csrf()).contentType("application/json").content(json(Map.of("email",account.email())))).andExpect(status().isAccepted());
        String replacement=token(account,"VERIFY"); assertThat(replacement).isNotEqualTo(original);
        mvc.perform(post("/api/auth/email/confirm").with(csrf()).contentType("application/json").content(json(Map.of("token",original)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/email/confirm").with(csrf()).contentType("application/json").content(json(Map.of("token",replacement)))).andExpect(status().isOk());
    }
    @Test void simultaneousUseOfResetLinkHasOnlyOneWinner() throws Exception {
        Account account=account(); recovery(account); String code=token(account,"RESET");
        var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=()->{start.await(); return mvc.perform(post("/api/auth/password/reset").with(csrf()).contentType("application/json")
                    .content(json(Map.of("token",code,"password",NEW_PASSWORD)))).andReturn().getResponse().getStatus();};
            var a=workers.submit(attempt); var b=workers.submit(attempt); start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,400);
        }
        assertThat(jdbc.queryForObject("select credentials_version from app_user where id=?",Integer.class,account.id())).isEqualTo(1);
    }
    @Test void expiredDemoMailboxCannotIssueRecoveryUntilTheOwnerReopensIt() throws Exception {
        Account account=account();
        jdbc.update("update app_user set demo_mailbox_expires_at=? where id=?",at("06:59").minusDays(1),account.id());
        recovery(account);
        assertThat(jdbc.queryForObject("select count(*) from account_action where user_id=? and purpose='RESET'",Integer.class,account.id())).isZero();
        mvc.perform(get("/api/auth/demo-mailbox").cookie(account.cookie())).andExpect(content().json("[]"));
        var result=mvc.perform(post("/api/auth/demo-mailbox/open").session(login(account,PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).startsWith(AccountAccessController.COOKIE+"=");
    }
    @Test void rolledBackTransactionCannotExposeAnEmail() throws Exception {
        Account account=account(); String digest=jdbc.queryForObject("select demo_mailbox_digest from app_user where id=?",String.class,account.id());
        int before=mails.preview(digest).size();
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            events.publishEvent(new OutgoingMail(UUID.randomUUID(),digest,"RESET",account.email(),"Não enviar","Transação desfeita","https://example.test/#/secret",java.time.Instant.parse("2030-06-03T10:15:00Z"),account.id()));
            status.setRollbackOnly();
        });
        assertThat(mails.preview(digest)).hasSize(before);
    }
    @Test void simulatedConfirmationAndLinksCannotAuthorizeRealEmailMode() throws Exception {
        Account account=account(); recovery(account); String reset=token(account,"RESET"), verification=token(account,"VERIFY");
        var realSettings=new MailSettings("resend","https://agenda.example","from@example.test","fake-key",false,false,true);
        var realAccess=new AccountAccessService(jdbc,passwords,realSettings,java.time.Clock.fixed(java.time.Instant.parse("2030-06-03T10:00:00Z"),java.time.ZoneOffset.UTC),events);
        assertThatThrownBy(()->realAccess.reset(reset,NEW_PASSWORD)).hasMessageContaining("Link inválido");
        assertThatThrownBy(()->realAccess.verify(verification)).hasMessageContaining("Link inválido");
        mvc.perform(post("/api/auth/email/confirm").with(csrf()).contentType("application/json").content(json(Map.of("token",verification)))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select email_verified_at from app_user where id=?",java.time.OffsetDateTime.class,account.id())).isNull();
        assertThat(jdbc.queryForObject("select demo_email_verified_at from app_user where id=?",java.time.OffsetDateTime.class,account.id())).isNotNull();
        jdbc.update("update business set public_booking_enabled=true where id=?",account.businessId());
        var realCatalog=new br.com.luizcode.agenda.catalog.CatalogRepository(jdbc,realSettings);
        assertThatThrownBy(()->realCatalog.business(catalog.business(account.businessId()).slug())).isInstanceOf(br.com.luizcode.agenda.shared.BusinessException.class);
        var setup=new br.com.luizcode.agenda.catalog.BusinessSetupService(jdbc,realCatalog,java.time.Clock.systemUTC(),realSettings,60).get(account.businessId());
        assertThat(setup.emailVerified()).isFalse(); assertThat(setup.published()).isFalse();
    }
    @Test void allAccountActionsRequireCsrfAndMalformedEmailIsValidationError() throws Exception {
        for(String endpoint:List.of("/api/auth/email/request","/api/auth/email/confirm","/api/auth/password/request","/api/auth/password/reset","/api/auth/demo-mailbox/open"))
            mvc.perform(post(endpoint).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password/request").with(csrf()).contentType("application/json").content("{\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
    }
}
