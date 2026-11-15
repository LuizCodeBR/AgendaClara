package br.com.luizcode.agenda.scheduling;

import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class DailySummaryService {
    public record Contact(@NotNull @Size(max=32) String phone,@NotNull @Min(0) Integer version){
        @Override public String toString(){return "Contact[protected]";}
    }
    public record Professional(UUID id,String name,String phone,int version){
        @Override public String toString(){return "Professional[id="+id+"]";}
    }
    public record Item(Instant startsAt,String customerName,String serviceName){}
    public record Summary(LocalDate date,String zoneId,String businessName,Professional professional,Instant generatedAt,List<Item> appointments){
        @Override public String toString(){return "Summary[date="+date+",count="+appointments.size()+"]";}
    }
    private final JdbcTemplate jdbc;
    private final CatalogRepository catalog;
    private final Clock clock;
    public DailySummaryService(JdbcTemplate jdbc,CatalogRepository catalog,Clock clock){this.jdbc=jdbc;this.catalog=catalog;this.clock=clock;}

    private Professional professional(UUID business,UUID id){
        return jdbc.query("select id,name,whatsapp_phone,version from professional where business_id=? and id=?",
            (r,n)->new Professional(r.getObject(1,UUID.class),line(r.getString(2)),r.getString(3),r.getInt(4)),business,id)
            .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Summary read(UUID business,UUID id,LocalDate date){
        var company=catalog.business(business);var zone=ZoneId.of(company.zoneId());
        var today=LocalDate.now(clock.withZone(zone));
        if(date==null||date.isBefore(today.minusDays(365))||date.isAfter(today.plusDays(365)))
            throw BusinessException.invalid("Escolha uma data até um ano antes ou depois de hoje.");
        var person=professional(business,id);
        var start=date.atStartOfDay(zone).toOffsetDateTime();var end=date.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        var items=jdbc.query("""
            select starts_at,customer_name,service_name from appointment
            where business_id=? and professional_id=? and status='BOOKED' and starts_at>=? and starts_at<?
            order by starts_at,id limit 201
            """,(r,n)->new Item(r.getObject(1,OffsetDateTime.class).toInstant(),line(r.getString(2)),line(r.getString(3))),business,id,start,end);
        if(items.size()>200)throw BusinessException.invalid("Este dia excede o limite de 200 atendimentos do resumo. Consulte a agenda.");
        return new Summary(date,zone.getId(),line(company.name()),person,clock.instant(),List.copyOf(items));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Professional saveContact(UUID business,UUID id,Contact contact){
        String phone=normalizePhone(contact.phone());
        catalog.lockProfessional(business,id);
        var current=professional(business,id);
        if(contact.version()==null||contact.version()!=current.version())
            throw BusinessException.conflict("O cadastro mudou em outra janela. Atualize o resumo antes de salvar o contato.");
        if(phone.equals(current.phone()))return current;
        jdbc.update("update professional set whatsapp_phone=?,version=version+1 where business_id=? and id=?",phone,business,id);
        return professional(business,id);
    }
    private static String normalizePhone(String input){
        if(input==null||input.length()>32)throw BusinessException.invalid("Informe um telefone com DDI, como +55 11 99999-1234.");
        String value=input.strip();if(value.isEmpty())return "";
        if(!value.matches("\\+?[0-9() .-]+"))throw BusinessException.invalid("Use apenas DDI, números e separadores de telefone.");
        String digits=value.replaceAll("[^0-9]","");
        if(!digits.matches("[1-9][0-9]{7,14}"))throw BusinessException.invalid("Informe de 8 a 15 dígitos com DDI, sem zero inicial.");
        return digits;
    }
    // Keep untrusted labels on a single line, including Unicode direction controls.
    private static String line(String value){return value.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]+"," ").replaceAll("\\s+"," ").strip();}
}
