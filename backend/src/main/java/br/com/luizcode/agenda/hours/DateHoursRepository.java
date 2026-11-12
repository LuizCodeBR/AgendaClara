package br.com.luizcode.agenda.hours;

import br.com.luizcode.agenda.catalog.WorkingWindow;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DateHoursRepository {
    public record DateHours(LocalDate date, String reason, List<WorkingWindow> hours) {}
    private static final class Day {
        final LocalDate date;
        final String reason;
        final List<WorkingWindow> hours = new ArrayList<>();
        Day(LocalDate date, String reason) { this.date=date; this.reason=reason; }
    }
    private final JdbcTemplate jdbc;
    public DateHoursRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Map<LocalDate, DateHours> between(UUID businessId, UUID professionalId, LocalDate first, LocalDate last) {
        Map<LocalDate, Day> rows = new LinkedHashMap<>();
        jdbc.query("""
                select e.exception_date, e.reason, w.opens_at, w.closes_at
                from date_hours e left join date_hours_window w on w.exception_id=e.id and w.business_id=e.business_id
                where e.business_id=? and e.professional_id=? and e.exception_date between ? and ?
                order by e.exception_date, w.opens_at
                """, rs -> {
                    LocalDate date=rs.getObject(1,LocalDate.class);
                    Day day=rows.get(date);
                    if(day==null) { day=new Day(date,rs.getString(2)); rows.put(date,day); }
                    LocalTime opens=rs.getObject(3,LocalTime.class);
                    if(opens!=null) day.hours.add(new WorkingWindow(opens,rs.getObject(4,LocalTime.class)));
                }, businessId,professionalId,first,last);
        Map<LocalDate, DateHours> result=new LinkedHashMap<>();
        rows.forEach((date,day)->result.put(date,new DateHours(date,day.reason,List.copyOf(day.hours))));
        return Collections.unmodifiableMap(result);
    }

    public void replace(UUID businessId, UUID professionalId, LocalDate date, String reason, List<WorkingWindow> hours) {
        delete(businessId,professionalId,date);
        UUID id=UUID.randomUUID();
        jdbc.update("insert into date_hours(id,business_id,professional_id,exception_date,reason) values (?,?,?,?,?)",
                id,businessId,professionalId,date,reason);
        for(var window:hours) jdbc.update("""
                insert into date_hours_window(id,business_id,exception_id,opens_at,closes_at) values (?,?,?,?,?)
                """,UUID.randomUUID(),businessId,id,window.opensAt(),window.closesAt());
    }

    public boolean delete(UUID businessId, UUID professionalId, LocalDate date) {
        var ids=jdbc.query("select id from date_hours where business_id=? and professional_id=? and exception_date=?",
                (rs,row)->rs.getObject(1,UUID.class),businessId,professionalId,date);
        if(ids.isEmpty()) return false;
        jdbc.update("delete from date_hours_window where business_id=? and exception_id=?",businessId,ids.getFirst());
        jdbc.update("delete from date_hours where business_id=? and id=?",businessId,ids.getFirst());
        return true;
    }
}
