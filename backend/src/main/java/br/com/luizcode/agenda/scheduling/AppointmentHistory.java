package br.com.luizcode.agenda.scheduling;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentHistory {
    public record Actor(String type,UUID userId,String name){
        public static Actor customer(){return new Actor("CUSTOMER",null,"Cliente");}
        public static Actor system(){return new Actor("SYSTEM",null,"Sistema");}
        public static Actor owner(br.com.luizcode.agenda.identity.AgendaUser user){return new Actor("OWNER",user.id(),user.name());}
    }
    public record Entry(UUID id,int version,String action,String fromStatus,String toStatus,Instant fromStart,Instant fromEnd,
        Instant toStart,Instant toEnd,String actorType,String actorName,String reason,Instant occurredAt){}
    public record Page(List<Entry> events,Integer nextBeforeVersion){}
    public record Attempt(UUID appointmentId,int expectedVersion,String status,String reason){}
    private final JdbcTemplate jdbc;
    public AppointmentHistory(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public Optional<Attempt> attempt(UUID business,UUID key){
        return jdbc.query("select appointment_id,expected_version,to_status,reason from appointment_history where business_id=? and request_key=?",
            (r,n)->new Attempt(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getString(4)),business,key).stream().findFirst();
    }
    public void append(ScheduleRepository.Appointment before,ScheduleRepository.Appointment after,String action,Actor actor,
            String reason,Instant now,UUID key,Integer expected){
        jdbc.update("""
            insert into appointment_history(id,business_id,appointment_id,appointment_version,action,from_status,to_status,
                from_start,from_end,to_start,to_end,actor_type,actor_user_id,actor_name,reason,occurred_at,request_key,expected_version)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),after.businessId(),after.id(),after.version(),action,before==null?null:before.status(),after.status(),
            before==null?null:utc(before.startsAt()),before==null?null:utc(before.endsAt()),utc(after.startsAt()),utc(after.endsAt()),
            actor.type(),actor.userId(),actor.name(),reason,utc(now),key,expected);
    }
    public Page page(UUID business,UUID appointment,Integer before){
        var rows=jdbc.query("""
            select * from appointment_history where business_id=? and appointment_id=? and appointment_version<?
            order by appointment_version desc limit 51
            """,(r,n)->new Entry(r.getObject("id",UUID.class),r.getInt("appointment_version"),r.getString("action"),
                r.getString("from_status"),r.getString("to_status"),instant(r.getObject("from_start",OffsetDateTime.class)),instant(r.getObject("from_end",OffsetDateTime.class)),
                instant(r.getObject("to_start",OffsetDateTime.class)),instant(r.getObject("to_end",OffsetDateTime.class)),r.getString("actor_type"),
                r.getString("actor_name"),r.getString("reason"),instant(r.getObject("occurred_at",OffsetDateTime.class))),
            business,appointment,before==null?Integer.MAX_VALUE:before);
        return new Page(List.copyOf(rows.subList(0,Math.min(rows.size(),50))),rows.size()>50?rows.get(49).version():null);
    }
    private static Instant instant(OffsetDateTime t){return t==null?null:t.toInstant();}
    private static OffsetDateTime utc(Instant t){return t.atOffset(ZoneOffset.UTC);}
}
