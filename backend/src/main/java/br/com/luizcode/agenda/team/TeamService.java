package br.com.luizcode.agenda.team;

import static br.com.luizcode.agenda.team.TeamModels.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.catalog.EffectiveHours;
import br.com.luizcode.agenda.hours.DateHoursRepository;
import java.time.LocalDate;
import java.util.Map;
import br.com.luizcode.agenda.catalog.WorkingHoursPolicy;
import br.com.luizcode.agenda.catalog.WorkingWindow;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {
    private final TeamRepository team;
    private final CatalogRepository catalog;
    private final Clock clock;
    private final EffectiveHours effectiveHours;
    public TeamService(TeamRepository team, CatalogRepository catalog, Clock clock, EffectiveHours effectiveHours) {
        this.team = team; this.catalog = catalog; this.clock = clock; this.effectiveHours=effectiveHours;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<ProfessionalConfiguration> list(UUID businessId) { return team.list(businessId); }

    @Transactional
    public ProfessionalConfiguration create(UUID businessId, CreateProfessional request) {
        return team.create(businessId, request);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProfessionalConfiguration update(UUID businessId, UUID id, UpdateProfessional request) {
        // Booking, configuration and deactivation share the same database row lock.
        catalog.lockProfessional(businessId, id);
        var current = team.find(businessId, id);
        if (current.version() != request.version())
            throw BusinessException.conflict("Os dados foram alterados em outra sessão. Recarregue antes de salvar.");
        if (request.serviceIds() == null || request.serviceIds().size() > 500 || request.serviceIds().stream().anyMatch(java.util.Objects::isNull))
            throw BusinessException.invalid("Informe os serviços atendidos pelo profissional.");
        var serviceIds = new java.util.HashSet<>(request.serviceIds());
        if (serviceIds.size() != request.serviceIds().size())
            throw BusinessException.invalid("Não repita um serviço na mesma configuração.");
        var ownedServices = catalog.services(businessId).stream().map(br.com.luizcode.agenda.catalog.CatalogModels.Service::id).collect(java.util.stream.Collectors.toSet());
        if (!ownedServices.containsAll(serviceIds))
            throw BusinessException.invalid("Escolha apenas serviços cadastrados neste estabelecimento.");
        var hours = normalized(request.hours());
        var commitments = team.upcoming(businessId, id, clock.instant());
        if (!request.active() && !commitments.isEmpty())
            throw BusinessException.conflict("Há atendimentos confirmados futuros ou em andamento. Remarque ou cancele antes de inativar o profissional.");
        ZoneId zone = ZoneId.of(catalog.business(businessId).zoneId());
        Map<LocalDate,DateHoursRepository.DateHours> exceptions=Map.of();
        if(!commitments.isEmpty()) exceptions=effectiveHours.exceptions(businessId,id,
                commitments.getFirst().startsAt().atZone(zone).toLocalDate(),
                commitments.getLast().startsAt().atZone(zone).toLocalDate());
        for (var commitment : commitments) {
            if (!serviceIds.contains(commitment.serviceId()))
                throw BusinessException.conflict("O agendamento " + commitment.reference()
                        + " utiliza um serviço que seria removido deste profissional. Remarque ou cancele antes de alterar os serviços atendidos.");
            LocalDate date=commitment.startsAt().atZone(zone).toLocalDate();
            var special=exceptions.get(date);
            var windows = special==null ? hours.stream()
                    .filter(window -> window.weekday() == date.getDayOfWeek().getValue())
                    .map(window -> new WorkingWindow(window.opensAt(), window.closesAt())).toList() : special.hours();
            if (!WorkingHoursPolicy.contains(commitment.startsAt(), commitment.endsAt(), zone, windows)) {
                String when = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").format(commitment.startsAt().atZone(zone));
                throw BusinessException.conflict("O agendamento " + commitment.reference() + ", em " + when
                        + ", ficaria fora do expediente. Remarque ou cancele esse atendimento antes de alterar os horários.");
            }
        }
        team.replace(businessId, id, request, hours);
        return team.find(businessId, id);
    }

    private List<WeeklyWindow> normalized(List<WeeklyWindow> input) {
        List<WeeklyWindow> result=new ArrayList<>();
        for(int weekday=1;weekday<=7;weekday++) {
            int day=weekday;
            var windows=input.stream().filter(window->window.weekday()==day)
                    .map(window->new WorkingWindow(window.opensAt(),window.closesAt())).toList();
            for(var window:WorkingHoursPolicy.normalize(windows))
                result.add(new WeeklyWindow(day,window.opensAt(),window.closesAt()));
        }
        return List.copyOf(result);
    }
}
