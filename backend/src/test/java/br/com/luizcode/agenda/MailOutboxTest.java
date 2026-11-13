package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.identity.*;
import br.com.luizcode.agenda.identity.AccessModels.*;
import br.com.luizcode.agenda.mail.*;
import br.com.luizcode.agenda.mail.MailQueueModels.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

class MailOutboxTest extends ApiTestBase {
    @Autowired MailOutboxRepository outbox;
    @Autowired MailPayloadCipher cipher;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationEventPublisher events;
    @Autowired Clock clock;
    @TempDir Path directory;
    record Owner(UUID id,UUID business,String email,String digest) {}
    @BeforeEach void clearBeats() { jdbc.update("delete from mail_worker_heartbeat"); }
    @AfterEach void cleanup() {
        for(UUID id:jdbc.query("select id from business where name='Queue teste'",(rs,row)->rs.getObject(1,UUID.class))) {
            jdbc.update("delete from mail_outbox where business_id=?",id);
            jdbc.update("delete from account_action where user_id in(select id from app_user where business_id=?)",id);
            jdbc.update("delete from app_user where business_id=?",id); jdbc.update("delete from business where id=?",id);
        }
        jdbc.update("delete from mail_worker_heartbeat");
    }
    Owner ownerAccount() {
        UUID business=UUID.randomUUID(),user=UUID.randomUUID(); String email=UUID.randomUUID()+"@example.test",digest=UUID.randomUUID().toString();
        jdbc.update("insert into business(id,slug,name,category,zone_id,address) values(?,?,?,?,?,?)",business,"queue-"+business,"Queue teste","Teste","America/Sao_Paulo","Online");
        jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name,demo_mailbox_digest,demo_mailbox_expires_at) values(?,?,?,?,?,?,?)",
                user,business,email,"unused","Ana Teste",digest,now().plusHours(1));
        return new Owner(user,business,email,digest);
    }
    OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }
    OutgoingMail message(Owner owner,UUID id) {
        return new OutgoingMail(id,owner.digest(),"RESET",owner.email(),"Assunto privado","Texto privado",
                "https://example.test/#/redefinir-senha?token=secret-"+id,clock.instant().plusSeconds(900),owner.id());
    }
    UUID enqueue(Owner owner,String mode) {
        UUID id=UUID.randomUUID(); var mail=message(owner,id);
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("insert into account_action(id,user_id,purpose,token_digest,created_at,expires_at,simulated) values(?,?,'RESET',?,?,?,?)",
                    id,owner.id(),id.toString().replace("-","").repeat(2),now(),now().plusMinutes(15),mode.equals("demo"));
            outbox.record(mail,mode,"snapshot@example.test",cipher);
        });
        return id;
    }
    String state(UUID id) { return jdbc.queryForObject("select status from mail_outbox where id=?",String.class,id); }
    void due(UUID id) { jdbc.update("update mail_outbox set next_attempt_at=? where id=?",now().minusSeconds(1),id); }
    MailSettings real() { return new MailSettings("resend","https://example.test","different@example.test","fake-key",false,false,true); }
    MailDispatcher worker(ResendTransport transport) { return new MailDispatcher(real(),transport,clock,outbox,cipher,true); }

    @Test void persistenceProtectsContentsAndPreviewSurvivesRecreatingTheCipherAndReader() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"demo");
        String encrypted=jdbc.queryForObject("select payload from mail_outbox where id=?",String.class,id);
        assertThat(encrypted).doesNotContain(owner.email(),"secret-","Texto privado","snapshot@example.test");
        var settings=new MailSettings("demo","","","",true,false,false);
        var reloaded=new MailPayloadCipher(jdbc,mapper,settings,clock,"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",directory.resolve("unused.key").toString());
        var stored=outbox.previews(owner.digest()).getFirst();
        assertThat(reloaded.decrypt(stored.aad(),stored.payload()).to()).isEqualTo(owner.email());
        assertThat(stored.id()).isEqualTo(id); assertThat(outbox.previews("other")).isEmpty();
        assertThat(state(id)).isEqualTo("ACCEPTED");
    }
    @Test void encryptionUsesRandomNonceAndBindsMessageAndTenantAndDoesNotPrintSecrets() {
        var payload=new Payload("from@example.test","private@example.test","Assunto","segredo","https://example.test/#/secret");
        String one=cipher.encrypt("tenant-A",payload),two=cipher.encrypt("tenant-A",payload);
        assertThat(one).isNotEqualTo(two);
        assertThat(cipher.decrypt("tenant-A",one)).isEqualTo(payload);
        assertThatThrownBy(()->cipher.decrypt("tenant-B",one)).hasMessageNotContaining("segredo");
        assertThatThrownBy(()->cipher.decrypt("tenant-A",one.substring(0,one.length()-4)+"AAAA")).isInstanceOf(IllegalStateException.class);
        assertThat(payload.toString()).doesNotContain("private@example.test","segredo");
    }
    @Test void localKeyIsCreatedOnceAndReusedAndMissingOrWrongKeyDoesNotReplaceExistingContents() throws Exception {
        var demo=new MailSettings("demo","","","",true,false,false);
        var isolated=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:key-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        isolated.execute("create table mail_outbox(key_id varchar(64),payload text,expires_at timestamp with time zone)");
        isolated.execute("create table mail_key_identity(id integer primary key,key_id varchar(64))");
        Path key=directory.resolve("mail-outbox.key");
        var generated=new MailPayloadCipher(isolated,mapper,demo,clock,"",key.toString());
        String value=Files.readString(key);
        var same=new MailPayloadCipher(isolated,mapper,demo,clock,"",key.toString());
        assertThat(same.keyId()).isEqualTo(generated.keyId()); assertThat(Files.readString(key)).isEqualTo(value);
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"demo");
        assertThatThrownBy(()->new MailPayloadCipher(jdbc,mapper,demo,clock,"",directory.resolve("missing.key").toString())).hasMessageContaining("Restaure");
        assertThatThrownBy(()->new MailPayloadCipher(jdbc,mapper,demo,clock,"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",key.toString())).hasMessageContaining("não corresponde");
        assertThat(jdbc.queryForObject("select payload from mail_outbox where id=?",String.class,id)).isNotNull();
    }
    @Test void outboxInsertionFailureRollsBackCredentialChangesInTheSameTransaction() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"demo");
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            jdbc.update("update app_user set credentials_version=credentials_version+1 where id=?",owner.id());
            events.publishEvent(message(owner,id));
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select credentials_version from app_user where id=?",Integer.class,owner.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox where id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void transientFailurePersistsAndFreshWorkerRetriesWithSameIdAndSenderSnapshot() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend"); ResendTransport transport=mock(ResendTransport.class);
        when(transport.send(eq(id),any(Payload.class))).thenReturn(Delivery.failed("NETWORK",true,0),Delivery.ok());
        worker(transport).dispatchPending(); assertThat(state(id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select attempts from mail_outbox where id=?",Integer.class,id)).isEqualTo(1);
        worker(transport).dispatchPending(); verify(transport,times(1)).send(eq(id),any(Payload.class));
        due(id); worker(transport).dispatchPending();
        assertThat(state(id)).isEqualTo("ACCEPTED");
        var captured=org.mockito.ArgumentCaptor.forClass(Payload.class);
        verify(transport,times(2)).send(eq(id),captured.capture());
        assertThat(captured.getAllValues()).allSatisfy(payload->assertThat(payload.from()).isEqualTo("snapshot@example.test"));
        assertThat(jdbc.queryForObject("select payload from mail_outbox where id=?",String.class,id)).isNull();
    }
    @Test void twoWorkersCannotClaimTheSameMessageAndAnExpiredLeaseCanBeRecovered() throws Exception {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(4)) {
            var futures=new ArrayList<Future<Optional<Job>>>();
            for(int i=0;i<4;i++) futures.add(workers.submit(()->{start.await(); return outbox.claim("resend");}));
            start.countDown(); var claimed=new ArrayList<Job>();
            for(var future:futures) future.get(20,TimeUnit.SECONDS).ifPresent(claimed::add);
            assertThat(claimed).hasSize(1);
            Job old=claimed.getFirst();
            assertThat(outbox.claim("resend")).isEmpty();
            jdbc.update("update mail_outbox set lease_until=? where id=?",now().minusSeconds(1),id);
            Job recovered=outbox.claim("resend").orElseThrow();
            assertThat(recovered.leaseToken()).isNotEqualTo(old.leaseToken());
            assertThat(outbox.finish(old,Delivery.ok())).isFalse();
            assertThat(outbox.finish(recovered,Delivery.ok())).isTrue();
            assertThat(state(id)).isEqualTo("ACCEPTED");
        }
    }
    @Test void automaticRetriesStopAndOneManualCycleIsAllowedOnlyToTheOwner() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        for(int i=1;i<=3;i++) { due(id); Job job=outbox.claim("resend").orElseThrow(); outbox.finish(job,Delivery.failed("TEMPORARY",true,0)); }
        assertThat(state(id)).isEqualTo("FAILED");
        assertThat(outbox.claim("resend")).isEmpty();
        assertThatThrownBy(()->outbox.retry(UUID.randomUUID(),id,"resend")).isInstanceOf(br.com.luizcode.agenda.shared.BusinessException.class);
        outbox.retry(owner.business(),id,"resend");
        assertThat(state(id)).isEqualTo("PENDING");
        for(int i=1;i<=3;i++) { due(id); Job job=outbox.claim("resend").orElseThrow(); outbox.finish(job,Delivery.failed("TEMPORARY",true,0)); }
        assertThatThrownBy(()->outbox.retry(owner.business(),id,"resend")).hasMessageContaining("não pode");
    }
    @Test void permanentFailuresDoNotAutomaticallyRetryAndRetryAfterIsHonored() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        Job first=outbox.claim("resend").orElseThrow(); outbox.finish(first,Delivery.failed("RATE_LIMIT",true,120));
        assertThat(jdbc.queryForObject("select next_attempt_at from mail_outbox where id=?",OffsetDateTime.class,id)).isEqualTo(now().plusSeconds(120));
        due(id); Job second=outbox.claim("resend").orElseThrow(); outbox.finish(second,Delivery.failed("AUTH",false,0));
        assertThat(state(id)).isEqualTo("FAILED"); assertThat(outbox.claim("resend")).isEmpty();
    }
    @Test void usedOrReplacedLinksAreCancelledBeforeSending() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        jdbc.update("update account_action set used_at=? where id=?",now(),id);
        assertThat(outbox.claim("resend")).isEmpty(); assertThat(state(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select payload from mail_outbox where id=?",String.class,id)).isNull();
    }
    @Test void expiryClearsSensitiveContentsAndRetentionRemovesOnlyOldTerminalRecords() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"demo");
        jdbc.update("update mail_outbox set expires_at=? where id=?",now().minusSeconds(1),id);
        outbox.maintain(); assertThat(state(id)).isEqualTo("EXPIRED");
        assertThat(outbox.previews(owner.digest())).isEmpty();
        assertThat(jdbc.queryForObject("select payload from mail_outbox where id=?",String.class,id)).isNull();
        jdbc.update("update mail_outbox set created_at=? where id=?",now().minusDays(8),id); outbox.maintain();
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox where id=?",Integer.class,id)).isZero();
    }
    @Test void lastInterruptedAttemptBecomesVisibleFailureAndExpiredDeadlineNeverSends() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        jdbc.update("update mail_outbox set status='PROCESSING',attempts=3,lease_until=? where id=?",now().minusSeconds(1),id);
        outbox.maintain(); assertThat(state(id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error from mail_outbox where id=?",String.class,id)).isEqualTo("INTERRUPTED");
        jdbc.update("update mail_outbox set status='PENDING',send_deadline=? where id=?",now().minusSeconds(1),id);
        outbox.maintain(); assertThat(state(id)).isEqualTo("EXPIRED"); assertThat(outbox.claim("resend")).isEmpty();
    }
    @Test void monitorIsTenantScopedFilteredAndReportsFailuresOrStaleHeartbeatWithoutSecrets() throws Exception {
        Owner a=ownerAccount(),b=ownerAccount(); UUID id=enqueue(a,"resend"); enqueue(b,"demo");
        UUID instance=UUID.randomUUID(); outbox.heartbeat(instance,"resend",null);
        Monitor good=outbox.monitor(a.business(),"resend",null);
        assertThat(good.health()).isEqualTo("OK"); assertThat(good.messages()).hasSize(1);
        assertThat(good.counts().get("PENDING")).isEqualTo(1);
        assertThat(json(good)).doesNotContain(a.email(),"snapshot@example.test","secret-","payload","key_id");
        jdbc.update("update mail_worker_heartbeat set checked_at=?",now().minusSeconds(91));
        assertThat(outbox.monitor(a.business(),"resend",null).health()).isEqualTo("ATTENTION");
        assertThat(outbox.monitor(a.business(),"resend","FAILED").messages()).isEmpty();
        var user=new AgendaUser(a.id(),a.business(),"Ana",a.email(),"unused");
        mvc.perform(get("/api/admin/emails").with(user(user)).param("businessId",b.business().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].id").value(id.toString()));
        mvc.perform(get("/api/admin/emails").param("status","FAILED")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/emails").with(user(user)).param("status","invalid")).andExpect(status().isBadRequest());
        var otherUser=new AgendaUser(b.id(),b.business(),"B",b.email(),"unused");
        mvc.perform(post("/api/admin/emails/"+id+"/retry").with(user(otherUser)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/emails/"+id+"/retry").with(user(user))).andExpect(status().isForbidden());
    }

    @Test void providerCooldownAlsoAppliesToTheManualRetryAfterTheLastAutomaticAttempt() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        for(int i=0;i<2;i++) { due(id); outbox.finish(outbox.claim("resend").orElseThrow(),Delivery.failed("TEMPORARY",true,0)); }
        due(id); outbox.finish(outbox.claim("resend").orElseThrow(),Delivery.failed("RATE_LIMIT",true,120));
        assertThat(state(id)).isEqualTo("FAILED");
        assertThat(outbox.monitor(owner.business(),"resend",null).messages().getFirst().canRetry()).isFalse();
        assertThatThrownBy(()->outbox.retry(owner.business(),id,"resend")).isInstanceOf(br.com.luizcode.agenda.shared.BusinessException.class);
        due(id);
        assertThat(outbox.monitor(owner.business(),"resend",null).messages().getFirst().canRetry()).isTrue();
        outbox.retry(owner.business(),id,"resend");
        assertThat(state(id)).isEqualTo("PENDING");
    }
    @Test void providerWaitBeyondTheLinkDeadlineCannotBeOverriddenManually() {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        outbox.finish(outbox.claim("resend").orElseThrow(),Delivery.failed("RATE_LIMIT",true,3600));
        assertThat(state(id)).isEqualTo("FAILED");
        assertThat(outbox.monitor(owner.business(),"resend",null).messages().getFirst().canRetry()).isFalse();
        assertThatThrownBy(()->outbox.retry(owner.business(),id,"resend")).isInstanceOf(br.com.luizcode.agenda.shared.BusinessException.class);
    }
    @Test void concurrentManualRetriesAllowOnlyOneNewCycle() throws Exception {
        Owner owner=ownerAccount(); UUID id=enqueue(owner,"resend");
        outbox.finish(outbox.claim("resend").orElseThrow(),Delivery.failed("AUTH",false,0));
        var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var results=new ArrayList<Future<Boolean>>();
            for(int i=0;i<2;i++) results.add(workers.submit(()->{
                start.await();
                try { outbox.retry(owner.business(),id,"resend"); return true; }
                catch(br.com.luizcode.agenda.shared.BusinessException ex) {
                    assertThat(ex.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT); return false;
                }
            }));
            start.countDown();
            var outcomes=new ArrayList<Boolean>();
            for(var result:results) outcomes.add(result.get(15,TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(true,false);
        }
        assertThat(jdbc.queryForObject("select manual_retries from mail_outbox where id=?",Integer.class,id)).isEqualTo(1);
        assertThat(state(id)).isEqualTo("PENDING");
    }

}