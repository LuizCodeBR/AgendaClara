package br.com.luizcode.agenda.mail;

import static br.com.luizcode.agenda.mail.MailQueueModels.*;
import br.com.luizcode.agenda.identity.AccessModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class MailOutboxRepository {
    public static final int MAX_ATTEMPTS=3;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private static final RowMapper<Job> JOB=(rs,row)->new Job(rs.getObject("id",UUID.class),rs.getObject("business_id",UUID.class),
            rs.getObject("user_id",UUID.class),rs.getString("purpose"),rs.getString("delivery_mode"),rs.getString("status"),
            rs.getString("payload"),rs.getInt("attempts"),rs.getInt("manual_retries"),instant(rs,"created_at"),instant(rs,"expires_at"),
            instant(rs,"send_deadline"),instant(rs,"next_attempt_at"),instant(rs,"lease_until"),rs.getObject("lease_token",UUID.class),
            instant(rs,"accepted_at"),rs.getString("last_error"));
    public MailOutboxRepository(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    private static Instant instant(ResultSet rs,String name) throws SQLException { var time=rs.getObject(name,OffsetDateTime.class); return time==null?null:time.toInstant(); }
    private static OffsetDateTime utc(Instant value) { return value==null?null:value.atOffset(ZoneOffset.UTC); }

    @Transactional(propagation=Propagation.MANDATORY)
    public void record(OutgoingMail mail,String mode,String from,MailPayloadCipher cipher) {
        if(!Set.of("demo","resend").contains(mode)||!Set.of("VERIFY","RESET","CHANGED").contains(mail.purpose())) throw new IllegalArgumentException("Tipo de e-mail inválido.");
        UUID business=jdbc.queryForObject("select business_id from app_user where id=?",UUID.class,mail.userId());
        Instant now=clock.instant(),deadline=mail.expiresAt().isBefore(now.plus(Duration.ofHours(23)))?mail.expiresAt():now.plus(Duration.ofHours(23));
        String aad=mail.id()+":"+business+":"+mail.userId()+":"+mail.purpose()+":"+mode;
        String encrypted=cipher.encrypt(aad,new Payload(from,mail.to(),mail.subject(),mail.text(),mail.actionUrl()));
        boolean demo=mode.equals("demo");
        jdbc.update("""
                insert into mail_outbox(id,business_id,user_id,purpose,delivery_mode,status,payload,key_id,created_at,expires_at,send_deadline,next_attempt_at,accepted_at)
                values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,mail.id(),business,mail.userId(),mail.purpose(),mode,demo?"ACCEPTED":"PENDING",encrypted,cipher.keyId(),
                utc(now),utc(mail.expiresAt()),utc(deadline),demo?null:utc(now),demo?utc(now):null);
    }
    @Transactional(readOnly=true)
    public List<Job> previews(String digest) {
        return jdbc.query("""
                select m.* from mail_outbox m join app_user u on u.id=m.user_id and u.business_id=m.business_id
                where u.demo_mailbox_digest=? and u.demo_mailbox_expires_at>? and m.delivery_mode='demo'
                and m.status='ACCEPTED' and m.expires_at>? and m.payload is not null
                order by m.created_at desc,m.queue_seq desc limit 5
                """,JOB,digest,utc(clock.instant()),utc(clock.instant()));
    }
    @Transactional
    public Optional<Job> claim(String mode) {
        Instant now=clock.instant();
        for(int skipped=0;skipped<20;skipped++) {
            var rows=jdbc.query("""
                    select * from mail_outbox where delivery_mode=? and send_deadline>? and expires_at>? and attempts<?
                    and ((status='PENDING' and next_attempt_at<=?) or (status='PROCESSING' and lease_until<=?))
                    order by created_at,queue_seq limit 1 for update skip locked
                    """,JOB,mode,utc(now),utc(now),MAX_ATTEMPTS,utc(now),utc(now));
            if(rows.isEmpty()) return Optional.empty();
            Job job=rows.getFirst();
            if(!active(job)) {
                jdbc.update("update mail_outbox set status='CANCELLED',payload=null,key_id=null,next_attempt_at=null,lease_token=null,lease_until=null,last_error='SUPERSEDED' where id=?",job.id());
                continue;
            }
            UUID lease=UUID.randomUUID();
            jdbc.update("update mail_outbox set status='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=?,next_attempt_at=null where id=?",
                    lease,utc(now.plusSeconds(60)),job.id());
            return Optional.of(find(job.id()));
        }
        return Optional.empty();
    }
    private boolean active(Job job) {
        if(job.purpose().equals("CHANGED")) return true;
        return jdbc.queryForObject("select count(*) from account_action where id=? and user_id=? and purpose=? and used_at is null and expires_at>? and simulated=?",
                Integer.class,job.id(),job.userId(),job.purpose(),utc(clock.instant()),job.mode().equals("demo"))==1;
    }
    private Job find(UUID id) { return jdbc.query("select * from mail_outbox where id=?",JOB,id).getFirst(); }
    @Transactional
    public boolean finish(Job job,Delivery result) {
        Instant now=clock.instant();
        if(result.accepted()) return jdbc.update("""
                update mail_outbox set status='ACCEPTED',payload=null,key_id=null,accepted_at=?,lease_until=null,lease_token=null,last_error=null
                where id=? and status='PROCESSING' and lease_token=?
                """,utc(now),job.id(),job.leaseToken())==1;
        boolean retry=result.retryable()&&job.attempts()<MAX_ATTEMPTS;
        int delay=Math.max(30*job.attempts(),Math.max(0,result.retryAfterSeconds()));
        Instant next=now.plusSeconds(delay);
        retry=retry&&next.isBefore(job.sendDeadline())&&next.isBefore(job.expiresAt());
        return jdbc.update("""
                update mail_outbox set status=?,next_attempt_at=?,lease_until=null,lease_token=null,last_error=?
                where id=? and status='PROCESSING' and lease_token=?
                """,retry?"PENDING":"FAILED",(retry || (result.retryable() && "RATE_LIMIT".equals(result.code())))?utc(next):null,result.code(),job.id(),job.leaseToken())==1;
    }
    @Transactional
    public void maintain() {
        Instant now=clock.instant();
        jdbc.update("""
                update mail_outbox set status='EXPIRED',payload=null,key_id=null,next_attempt_at=null,lease_until=null,lease_token=null,last_error=null
                where ((expires_at<=?) or (delivery_mode='resend' and send_deadline<=?))
                and (status in ('PENDING','FAILED') or (status='PROCESSING' and lease_until<=?) or (delivery_mode='demo' and status='ACCEPTED'))
                """,utc(now),utc(now),utc(now));
        jdbc.update("""
                update mail_outbox set status='FAILED',lease_until=null,lease_token=null,last_error='INTERRUPTED'
                where status='PROCESSING' and lease_until<=? and attempts>=?
                """,utc(now),MAX_ATTEMPTS);
        jdbc.update("delete from mail_outbox where created_at<? and status not in ('PENDING','PROCESSING')",utc(now.minus(Duration.ofDays(7))));
        jdbc.update("delete from mail_worker_heartbeat where checked_at<?",utc(now.minus(Duration.ofHours(1))));
    }
    @Transactional
    public void heartbeat(UUID instance,String mode,String error) {
        int updated=jdbc.update("update mail_worker_heartbeat set checked_at=?,delivery_mode=?,last_error=? where instance_id=?",utc(clock.instant()),mode,error,instance);
        if(updated==0) jdbc.update("insert into mail_worker_heartbeat(instance_id,delivery_mode,checked_at,last_error) values(?,?,?,?)",instance,mode,utc(clock.instant()),error);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Monitor monitor(UUID business,String mode,String status) {
        Map<String,Long> counts=new LinkedHashMap<>();
        for(String state:List.of("PENDING","PROCESSING","ACCEPTED","FAILED","EXPIRED","CANCELLED")) counts.put(state,0L);
        jdbc.query("select status,count(*) from mail_outbox where business_id=? and created_at>=? group by status",rs->{
            counts.put(rs.getString(1),rs.getLong(2));
        },business,utc(clock.instant().minus(Duration.ofDays(7))));
        var rows=jdbc.query("select * from mail_outbox where business_id=? and created_at>=?"+(status==null?"":" and status=?")+" order by created_at desc,queue_seq desc limit 50",
                JOB,status==null?new Object[]{business,utc(clock.instant().minus(Duration.ofDays(7)))}:new Object[]{business,utc(clock.instant().minus(Duration.ofDays(7))),status});
        var pending=jdbc.query("select min(created_at) from mail_outbox where business_id=? and status in ('PENDING','PROCESSING')",
                (rs,row)->rs.getObject(1,OffsetDateTime.class),business);
        Instant oldest=pending.getFirst()==null?null:pending.getFirst().toInstant();
        var beats=jdbc.query("select checked_at,last_error from mail_worker_heartbeat where delivery_mode=? order by checked_at desc limit 1",
                (rs,row)->new Object[]{rs.getObject(1,OffsetDateTime.class).toInstant(),rs.getString(2)},mode);
        Instant checked=beats.isEmpty()?null:(Instant)beats.getFirst()[0];
        String health=mode.equals("disabled")?"DISABLED":mode.equals("demo")?"SIMULATED":"OK";
        if(!mode.equals("disabled") && (checked==null||checked.isBefore(clock.instant().minusSeconds(90))||(!beats.isEmpty()&&beats.getFirst()[1]!=null))) health="ATTENTION";
        if(counts.get("FAILED")>0 || (oldest!=null&&oldest.isBefore(clock.instant().minusSeconds(300)))) health="ATTENTION";
        return new Monitor(mode,counts,oldest,checked,health,rows.stream().map(job->item(job,mode)).toList());
    }
    private Item item(Job job,String mode) {
        boolean retry=mode.equals("resend")&&job.status().equals("FAILED")&&job.manualRetries()==0&&job.payload()!=null&&job.mode().equals(mode)
                &&job.expiresAt().isAfter(clock.instant())&&job.sendDeadline().isAfter(clock.instant())
                &&(job.nextAttemptAt()==null||!job.nextAttemptAt().isAfter(clock.instant()))&&!"ENCRYPTION".equals(job.lastError());
        return new Item(job.id(),job.purpose(),job.mode(),job.status(),job.attempts(),job.manualRetries(),job.createdAt(),job.expiresAt(),
                job.nextAttemptAt(),job.acceptedAt(),job.lastError(),retry);
    }
    @Transactional
    public void retry(UUID business,UUID id,String mode) {
        var found=jdbc.query("select * from mail_outbox where business_id=? and id=? for update",JOB,business,id);
        if(found.isEmpty()) throw BusinessException.notFound();
        Job job=found.getFirst();
        if(!item(job,mode).canRetry()) throw BusinessException.conflict("Este envio não pode ser reagendado. Solicite um novo link, se necessário.");
        if(!active(job)) throw BusinessException.conflict("Este link já foi substituído ou utilizado. Solicite outro.");
        jdbc.update("update mail_outbox set status='PENDING',attempts=0,manual_retries=manual_retries+1,next_attempt_at=?,last_error=null where id=?",
                utc(clock.instant()),id);
    }
}
