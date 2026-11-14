package br.com.luizcode.agenda.backup;

import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.customer.*;
import br.com.luizcode.agenda.mail.MailPayloadCipher;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class CustomerBackupFixture {
    static void seed(JdbcTemplate jdbc,MailPayloadCipher cipher,UUID business,UUID person,UUID service) {
        UUID appointment=UUID.randomUUID();var now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("update business set booking_notice_minutes=120,cancel_notice_minutes=1440,reschedule_notice_minutes=720,booking_policy_version=3 where id=?",business);
        jdbc.update("""
            insert into appointment(id,business_id,professional_id,service_id,service_name,price_cents,customer_name,customer_phone,
            starts_at,ends_at,status,reference,request_key,request_fingerprint,created_at,updated_at,version)
            values (?,?,?,?,?,1200,'Cliente do backup','11999991234',?,?,'BOOKED',?,?,?, ?,?,4)
            """,appointment,business,person,service,"Serviço histórico",now.plusDays(1),now.plusDays(1).plusMinutes(30),
            UUID.randomUUID().toString().substring(0,10),UUID.randomUUID(),"f".repeat(64),now,now);
        jdbc.update("update appointment set booking_notice_minutes=60,cancel_notice_minutes=120,reschedule_notice_minutes=180,policy_version=2 where id=?",appointment);
        jdbc.update("update appointment set status='COMPLETED' where id=?",appointment);
        jdbc.update("""
            insert into appointment_history(id,business_id,appointment_id,appointment_version,action,from_status,to_status,
            from_start,from_end,to_start,to_end,actor_type,actor_name,reason,occurred_at)
            values(?,?,?,4,'OUTCOME','BOOKED','COMPLETED',?,?,?,?,'SYSTEM','Sistema','Resultado preservado no backup',?)
            """,UUID.randomUUID(),business,appointment,now.minusHours(1),now.minusMinutes(30),now.minusHours(1),now.minusMinutes(30),now);
        var keys=new CustomerKeys(cipher);var access=new CustomerAccessRepository(jdbc);
        access.save(business,appointment,3,CustomerKeys.digest(keys.token(appointment,3)),now.toInstant(),UUID.randomUUID());
        access.createGrant(access.access(business,appointment).orElseThrow(),CustomerKeys.digest("s".repeat(43)),null,now.toInstant(),now.plusHours(1).toInstant());
    }
    static void verify(JdbcTemplate jdbc,MailPayloadCipher original,MailPayloadCipher restored,UUID business) {
        var id=jdbc.queryForObject("select id from appointment where business_id=?",UUID.class,business);
        assertThat(jdbc.queryForObject("select version from appointment where id=?",Integer.class,id)).isEqualTo(4);
        var policy=new br.com.luizcode.agenda.policy.BookingPolicyService(jdbc).get(business);
        assertThat(policy).isEqualTo(new br.com.luizcode.agenda.policy.BookingPolicy(120,1440,720,3));
        var appointmentPolicy=jdbc.queryForObject("select cancel_notice_minutes from appointment where id=?",Integer.class,id);
        assertThat(appointmentPolicy).isEqualTo(120);
        assertThat(jdbc.queryForObject("select policy_version from appointment where id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from appointment where id=?",String.class,id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select reason from appointment_history where appointment_id=?",String.class,id)).isEqualTo("Resultado preservado no backup");
        var access=new CustomerAccessRepository(jdbc);
        var current=access.access(business,id).orElseThrow();
        String token=new CustomerKeys(restored).token(id,3);
        assertThat(token).isEqualTo(new CustomerKeys(original).token(id,3));
        assertThat(CustomerKeys.digest(token)).isEqualTo(current.digest());
        assertThat(current.generation()).isEqualTo(3);
        assertThat(access.grant(CustomerKeys.digest("s".repeat(43)),Instant.now()).orElseThrow().appointmentId()).isEqualTo(id);
        access.createGrant(current,CustomerKeys.digest("t".repeat(43)),null,Instant.now(),Instant.now().plusSeconds(600));
        assertThat(jdbc.queryForObject("select max(session_seq) from appointment_customer_session where appointment_id=?",Long.class,id)).isEqualTo(2);
    }
}
