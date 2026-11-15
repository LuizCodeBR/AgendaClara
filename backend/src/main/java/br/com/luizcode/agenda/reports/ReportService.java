package br.com.luizcode.agenda.reports;

import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.sql.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReportService {
    public static final int MAX_GROUPS=2000;
    public record Choice(UUID id,String name,boolean active){}
    public record Options(List<Choice> professionals,List<Choice> services){}
    public record Metrics(long total,long completed,long noShow,long cancelled,long pending,long upcoming,long completedValueCents){
        static Metrics zero(){return new Metrics(0,0,0,0,0,0,0);}
        Metrics plus(Metrics b){return new Metrics(safe(total+b.total),safe(completed+b.completed),safe(noShow+b.noShow),safe(cancelled+b.cancelled),safe(pending+b.pending),safe(upcoming+b.upcoming),safe(completedValueCents+b.completedValueCents));}
    }
    public record Row(UUID professionalId,String professionalName,boolean active,UUID serviceId,String serviceName,Metrics metrics){}
    public record Professional(UUID id,String name,boolean active,Metrics metrics){}
    public record Day(LocalDate date,Metrics metrics){}
    public record Report(LocalDate from,LocalDate to,String zoneId,String businessName,UUID professionalId,UUID serviceId,
                         String professionalLabel,String serviceLabel,Instant generatedAt,Metrics totals,List<Professional> professionals,List<Row> rows,List<Day> days){}
    private record Scope(String sql,List<Object> args){}
    private static final String AGG="""
        count(*) as total,
        sum(case when status='COMPLETED' then 1 else 0 end) as completed,
        sum(case when status='NO_SHOW' then 1 else 0 end) as no_show,
        sum(case when status='CANCELLED' then 1 else 0 end) as cancelled,
        sum(case when status='BOOKED' and ends_at<=? then 1 else 0 end) as pending,
        sum(case when status='BOOKED' then 1 else 0 end) as booked,
        sum(case when status='COMPLETED' then cast(price_cents as bigint) else 0 end) as completed_value
        """;
    private final JdbcTemplate jdbc;
    private final CatalogRepository catalog;
    private final Clock clock;
    public ReportService(JdbcTemplate jdbc,CatalogRepository catalog,Clock clock){this.jdbc=jdbc;this.catalog=catalog;this.clock=clock;}
    private static long safe(long value){if(value<0||value>9007199254740991L)throw BusinessException.invalid("Os totais excedem o limite do relatório. Reduza o período.");return value;}
    private static Metrics metrics(ResultSet r) throws SQLException {
        long pending=r.getLong("pending"),booked=r.getLong("booked");
        return new Metrics(safe(r.getLong("total")),safe(r.getLong("completed")),safe(r.getLong("no_show")),safe(r.getLong("cancelled")),safe(pending),safe(booked-pending),safe(r.getLong("completed_value")));
    }
    static String line(String value){return value.replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]+"," ").replaceAll("[\\s\\p{Zs}]+"," ").strip();}
    private Choice person(UUID business,UUID id){return jdbc.query("select id,name,active from professional where business_id=? and id=?",(r,n)->new Choice(r.getObject(1,UUID.class),line(r.getString(2)),r.getBoolean(3)),business,id).stream().findFirst().orElseThrow(BusinessException::notFound);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=15)
    public Options options(UUID business){
        catalog.business(business);
        var people=jdbc.query("select id,name,active from professional where business_id=? order by name,id limit 2001",(r,n)->new Choice(r.getObject(1,UUID.class),line(r.getString(2)),r.getBoolean(3)),business);
        var services=jdbc.query("select id,name from service_offering where business_id=? order by name,id limit 2001",(r,n)->new Choice(r.getObject(1,UUID.class),line(r.getString(2)),true),business);
        if(people.size()>MAX_GROUPS||services.size()>MAX_GROUPS)throw BusinessException.invalid("O cadastro excede o limite de 2000 opções do relatório.");
        return new Options(List.copyOf(people),List.copyOf(services));
    }
    private static Scope scope(UUID business,LocalDate from,LocalDate to,ZoneId zone,UUID professional,UUID service){
        var sql=new StringBuilder(" where a.business_id=? and a.starts_at>=? and a.starts_at<?");
        var args=new ArrayList<Object>(List.of(business,from.atStartOfDay(zone).toOffsetDateTime(),to.plusDays(1).atStartOfDay(zone).toOffsetDateTime()));
        if(professional!=null){sql.append(" and a.professional_id=?");args.add(professional);}
        if(service!=null){sql.append(" and a.service_id=?");args.add(service);}
        return new Scope(sql.toString(),args);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=15)
    public Report read(UUID business,LocalDate from,LocalDate to,UUID professional,UUID service){
        if(from==null||to==null||from.isAfter(to)||from.getYear()<1900||to.getYear()>2100||ChronoUnit.DAYS.between(from,to)>=90)
            throw BusinessException.invalid("Escolha um período de 1 a 90 dias, entre 1900 e 2100.");
        var instant=clock.instant();var now=instant.atOffset(ZoneOffset.UTC);
        var company=catalog.business(business);var zone=ZoneId.of(company.zoneId());
        String professionalLabel=professional==null?"Todos":person(business,professional).name();
        String serviceLabel=service==null?"Todos":line(catalog.service(business,service).name());
        var scope=scope(business,from,to,zone,professional,service);
        var args=new ArrayList<Object>();args.add(now);args.addAll(scope.args());
        var rows=jdbc.query("select a.professional_id,p.name,p.active,a.service_id,a.service_name,"+AGG+"""
             from appointment a join professional p on p.id=a.professional_id and p.business_id=a.business_id
            """+scope.sql()+" group by a.professional_id,p.name,p.active,a.service_id,a.service_name order by p.name,a.professional_id,a.service_name,a.service_id limit 2001",
            (r,n)->new Row(r.getObject(1,UUID.class),line(r.getString(2)),r.getBoolean(3),r.getObject(4,UUID.class),line(r.getString(5)),metrics(r)),args.toArray());
        if(rows.size()>MAX_GROUPS)throw BusinessException.invalid("O período excede 2000 combinações de profissional e serviço. Reduza o período ou use os filtros.");
        var totals=Metrics.zero();var people=new LinkedHashMap<UUID,Professional>();
        for(var row:rows){totals=totals.plus(row.metrics());var previous=people.get(row.professionalId());people.put(row.professionalId(),new Professional(row.professionalId(),row.professionalName(),row.active(),previous==null?row.metrics():previous.metrics().plus(row.metrics())));}
        // Explicit civil-day boundaries keep H2 and PostgreSQL identical across DST/session zones.
        var dayArgs=new ArrayList<Object>();dayArgs.add(now);var dayCase=new StringBuilder("case");
        for(var date=from;!date.isAfter(to);date=date.plusDays(1)){dayCase.append(" when a.starts_at<? then cast(? as date)");dayArgs.add(date.plusDays(1).atStartOfDay(zone).toOffsetDateTime());dayArgs.add(date);}
        dayCase.append(" end");dayArgs.addAll(scope.args());
        var existing=jdbc.query("select report_day,"+AGG+" from (select "+dayCase+" as report_day,a.status,a.ends_at,a.price_cents from appointment a"+scope.sql()+") grouped_days group by report_day order by report_day",
            (r,n)->new Day(r.getObject("report_day",LocalDate.class),metrics(r)),dayArgs.toArray());
        var daysByDate=new HashMap<LocalDate,Metrics>();for(var day:existing)daysByDate.put(day.date(),day.metrics());
        var days=new ArrayList<Day>();for(var date=from;!date.isAfter(to);date=date.plusDays(1))days.add(new Day(date,daysByDate.getOrDefault(date,Metrics.zero())));
        return new Report(from,to,zone.getId(),line(company.name()),professional,service,professionalLabel,serviceLabel,instant,totals,List.copyOf(people.values()),List.copyOf(rows),List.copyOf(days));
    }
}