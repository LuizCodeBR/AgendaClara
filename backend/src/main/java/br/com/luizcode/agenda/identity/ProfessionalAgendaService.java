package br.com.luizcode.agenda.identity;

import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;

@Service
public class ProfessionalAgendaService {
    public record Item(UUID id,String customerName,String serviceName,Instant startsAt,Instant endsAt,String status,int version){}
    public record Agenda(LocalDate date,String zoneId,String professionalName,List<Item> appointments){}
    public record Event(int version,String action,String status,String actor,Instant occurredAt){}
    public record History(List<Event> events,Integer nextBeforeVersion){}
    private final ProfessionalAccessService access;
    private final CatalogRepository catalog;
    private final JdbcTemplate jdbc;
    private final AppointmentOutcomeService outcomes;
    private final ScheduleRepository appointments;
    private final Clock clock;
    public ProfessionalAgendaService(ProfessionalAccessService access,CatalogRepository catalog,JdbcTemplate jdbc,AppointmentOutcomeService outcomes,ScheduleRepository appointments,Clock clock){
        this.access=access;this.catalog=catalog;this.jdbc=jdbc;this.outcomes=outcomes;this.appointments=appointments;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Agenda day(AgendaUser user,LocalDate date){
        access.requireCurrent(user,false);
        var company=catalog.business(user.businessId());var zone=ZoneId.of(company.zoneId());var today=LocalDate.now(clock.withZone(zone));
        if(date.isBefore(today.minusDays(365))||date.isAfter(today.plusDays(365)))throw BusinessException.invalid("Consulte uma data até um ano antes ou depois de hoje.");
        var rows=jdbc.query("""
            select id,customer_name,service_name,starts_at,ends_at,status,version from appointment
            where business_id=? and professional_id=? and starts_at>=? and starts_at<? order by starts_at,id limit 201
            """,(r,n)->new Item(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getObject(4,OffsetDateTime.class).toInstant(),r.getObject(5,OffsetDateTime.class).toInstant(),r.getString(6),r.getInt(7)),
            user.businessId(),user.professionalId(),date.atStartOfDay(zone).toOffsetDateTime(),date.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        if(rows.size()>200)throw BusinessException.invalid("A agenda excede 200 atendimentos. Solicite ajuda ao responsável.");
        return new Agenda(date,company.zoneId(),jdbc.queryForObject("select name from professional where id=?",String.class,user.professionalId()),rows);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Item change(AgendaUser user,UUID id,AppointmentOutcomeService.Change request){
        var result=outcomes.change(user,id,request);
        return new Item(result.id(),result.customerName(),result.serviceName(),result.startsAt(),result.endsAt(),result.status(),result.version());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public History history(AgendaUser user,UUID id,Integer before){
        access.requireCurrent(user,false);
        var item=appointments.appointment(user.businessId(),id);
        if(!item.professionalId().equals(user.professionalId()))throw BusinessException.notFound();
        var page=outcomes.history(user.businessId(),id,before);
        return new History(page.events().stream().map(e->new Event(e.version(),e.action(),e.toStatus(),
            switch(e.actorType()){case "OWNER"->"Responsável";case "PROFESSIONAL"->"Profissional";case "CUSTOMER"->"Cliente";default->"Sistema";},e.occurredAt())).toList(),page.nextBeforeVersion());
    }
}
