package br.com.luizcode.agenda.hours;

import static br.com.luizcode.agenda.hours.DateHoursModels.*;
import br.com.luizcode.agenda.catalog.*;
import br.com.luizcode.agenda.shared.BusinessException;
import br.com.luizcode.agenda.team.TeamRepository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DateHoursService {
    private final DateHoursRepository dates;
    private final TeamRepository team;
    private final CatalogRepository catalog;
    private final Clock clock;
    public DateHoursService(DateHoursRepository dates, TeamRepository team, CatalogRepository catalog, Clock clock) {
        this.dates=dates; this.team=team; this.catalog=catalog; this.clock=clock;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ExceptionCalendar list(UUID businessId, UUID professionalId) {
        return calendar(businessId,professionalId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ExceptionCalendar save(UUID businessId, UUID professionalId, LocalDate date, SaveException request) {
        catalog.lockProfessional(businessId,professionalId);
        checkVersion(businessId,professionalId,request.version());
        ZoneId zone=validateDate(businessId,date);
        var windows=WorkingHoursPolicy.normalize(request.hours().stream()
                .map(window->new WorkingWindow(window.opensAt(),window.closesAt())).toList());
        protect(businessId,professionalId,date,zone,windows);
        dates.replace(businessId,professionalId,date,request.reason().strip(),windows);
        team.advanceVersion(businessId,professionalId,request.version());
        return calendar(businessId,professionalId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ExceptionCalendar remove(UUID businessId, UUID professionalId, LocalDate date, int version) {
        catalog.lockProfessional(businessId,professionalId);
        checkVersion(businessId,professionalId,version);
        ZoneId zone=validateDate(businessId,date);
        if(!dates.between(businessId,professionalId,date,date).containsKey(date)) throw BusinessException.notFound();
        protect(businessId,professionalId,date,zone,catalog.hours(businessId,professionalId,date.getDayOfWeek().getValue()));
        dates.delete(businessId,professionalId,date);
        team.advanceVersion(businessId,professionalId,version);
        return calendar(businessId,professionalId);
    }

    private void checkVersion(UUID businessId,UUID professionalId,int version) {
        if(version<0) throw BusinessException.invalid("A versão do cadastro é inválida.");
        if(team.find(businessId,professionalId).version()!=version)
            throw BusinessException.conflict("O cadastro ou os horários foram alterados em outra sessão. Recarregue antes de salvar.");
    }

    private ZoneId validateDate(UUID businessId,LocalDate date) {
        ZoneId zone=ZoneId.of(catalog.business(businessId).zoneId());
        LocalDate today=LocalDate.now(clock.withZone(zone));
        if(date.isBefore(today)||date.isAfter(today.plusDays(365)))
            throw BusinessException.invalid("Configure uma data entre hoje e os próximos 365 dias.");
        return zone;
    }

    private void protect(UUID businessId,UUID professionalId,LocalDate date,ZoneId zone,List<WorkingWindow> windows) {
        for(var commitment:team.upcomingOnDate(businessId,professionalId,clock.instant(),
                date.atStartOfDay(zone).toInstant(),date.plusDays(1).atStartOfDay(zone).toInstant())) {
            if(!WorkingHoursPolicy.contains(commitment.startsAt(),commitment.endsAt(),zone,windows)) {
                String when=DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").format(commitment.startsAt().atZone(zone));
                throw BusinessException.conflict("O agendamento "+commitment.reference()+", em "+when
                        +", ficaria fora do expediente. Remarque ou cancele esse atendimento antes de alterar esta data.");
            }
        }
    }

    private ExceptionCalendar calendar(UUID businessId,UUID professionalId) {
        var professional=team.find(businessId,professionalId);
        ZoneId zone=ZoneId.of(catalog.business(businessId).zoneId());
        LocalDate first=LocalDate.now(clock.withZone(zone)), last=first.plusDays(365);
        var items=dates.between(businessId,professionalId,first,last).values().stream()
                .map(day->new DateException(day.date(),day.reason(),day.hours().stream()
                        .map(window->new DayWindow(window.opensAt(),window.closesAt())).toList())).toList();
        return new ExceptionCalendar(professional.version(),first,last,items,professional.hours());
    }
}
