package br.com.luizcode.agenda.customer;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerAccessRepository {
    public record Access(UUID appointmentId,UUID businessId,UUID professionalId,int generation,String digest,Instant issuedAt,Instant revokedAt,UUID issueKey){}
    public record Grant(UUID appointmentId,UUID businessId,UUID professionalId,int generation,Instant expiresAt){}
    private final JdbcTemplate jdbc;
    private static final String ACCESS="""
            select c.*,a.professional_id from appointment_access c join appointment a
              on a.id=c.appointment_id and a.business_id=c.business_id
            """;
    private static final RowMapper<Access> ROW=(rs,row)->new Access(rs.getObject("appointment_id",UUID.class),
            rs.getObject("business_id",UUID.class),rs.getObject("professional_id",UUID.class),rs.getInt("generation"),rs.getString("token_digest"),
            rs.getObject("issued_at",OffsetDateTime.class).toInstant(),rs.getObject("revoked_at",OffsetDateTime.class)==null?null:rs.getObject("revoked_at",OffsetDateTime.class).toInstant(),rs.getObject("issue_request_key",UUID.class));
    public CustomerAccessRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Access> access(UUID business,UUID appointment){return jdbc.query(ACCESS+" where c.business_id=? and c.appointment_id=?",ROW,business,appointment).stream().findFirst();}
    public Optional<Access> byDigest(String digest){return jdbc.query(ACCESS+" where c.token_digest=? and c.revoked_at is null",ROW,digest).stream().findFirst();}
    public void save(UUID business,UUID appointment,int generation,String digest,Instant now,UUID requestKey){
        int changed=jdbc.update("update appointment_access set generation=?,token_digest=?,issued_at=?,revoked_at=null,issue_request_key=? where business_id=? and appointment_id=?",
                generation,digest,utc(now),requestKey,business,appointment);
        if(changed==0)jdbc.update("insert into appointment_access(appointment_id,business_id,generation,token_digest,issued_at,issue_request_key) values(?,?,?,?,?,?)",
                appointment,business,generation,digest,utc(now),requestKey);
        jdbc.update("delete from appointment_customer_session where business_id=? and appointment_id=?",business,appointment);
    }
    public void revoke(UUID business,UUID appointment,Instant now){
        jdbc.update("update appointment_access set generation=generation+1,token_digest=null,revoked_at=?,issue_request_key=null where business_id=? and appointment_id=?",
                utc(now),business,appointment);
        jdbc.update("delete from appointment_customer_session where business_id=? and appointment_id=?",business,appointment);
    }
    public Optional<Grant> grant(String digest,Instant now){
        return jdbc.query("""
                select s.appointment_id,s.business_id,s.access_generation,s.expires_at,a.professional_id
                from appointment_customer_session s join appointment_access c on c.appointment_id=s.appointment_id and c.business_id=s.business_id
                  and c.generation=s.access_generation and c.revoked_at is null and c.token_digest is not null
                join appointment a on a.id=s.appointment_id and a.business_id=s.business_id
                where s.secret_digest=? and s.expires_at>?
                """,(rs,row)->new Grant(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(5,UUID.class),
                        rs.getInt(3),rs.getObject(4,OffsetDateTime.class).toInstant()),digest,utc(now)).stream().findFirst();
    }
    public void createGrant(Access access,String digest,String previousDigest,Instant now,Instant expires){
        jdbc.update("delete from appointment_customer_session where appointment_id=? and (expires_at<=? or secret_digest=?)",access.appointmentId(),utc(now),previousDigest);
        int count=jdbc.queryForObject("select count(*) from appointment_customer_session where appointment_id=?",Integer.class,access.appointmentId());
        if(count>=8)jdbc.update("""
                delete from appointment_customer_session where id=(select id from appointment_customer_session
                where appointment_id=? order by created_at,session_seq limit 1)
                """,access.appointmentId());
        jdbc.update("insert into appointment_customer_session(id,appointment_id,business_id,access_generation,secret_digest,created_at,expires_at) values(?,?,?,?,?,?,?)",
                UUID.randomUUID(),access.appointmentId(),access.businessId(),access.generation(),digest,utc(now),utc(expires));
    }
    public void closeGrant(String digest){jdbc.update("delete from appointment_customer_session where secret_digest=?",digest);}
    private static OffsetDateTime utc(Instant value){return value.atOffset(ZoneOffset.UTC);}
}
