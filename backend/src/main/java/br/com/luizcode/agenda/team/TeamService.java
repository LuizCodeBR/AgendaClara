package br.com.luizcode.agenda.team;

import static br.com.luizcode.agenda.team.TeamModels.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.catalog.WorkingHoursPolicy;
import br.com.luizcode.agenda.catalog.WorkingWindow;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
    public TeamService(TeamRepository team, CatalogRepository catalog, Clock clock) {
        this.team = team; this.catalog = catalog; this.clock = clock;
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
        var hours = normalized(request.hours());
        var commitments = team.upcoming(businessId, id, clock.instant());
        if (!request.active() && !commitments.isEmpty())
            throw BusinessException.conflict("Há atendimentos confirmados futuros ou em andamento. Remarque ou cancele antes de inativar o profissional.");
        ZoneId zone = ZoneId.of(catalog.business(businessId).zoneId());
        for (var commitment : commitments) {
            var windows = hours.stream()
                    .filter(window -> window.weekday() == commitment.startsAt().atZone(zone).getDayOfWeek().getValue())
                    .map(window -> new WorkingWindow(window.opensAt(), window.closesAt())).toList();
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
        List<WeeklyWindow> sorted = input.stream().sorted(Comparator.comparingInt(WeeklyWindow::weekday)
                .thenComparing(WeeklyWindow::opensAt)).toList();
        List<WeeklyWindow> result = new ArrayList<>();
        for (int weekday = 1; weekday <= 7; weekday++) {
            int day = weekday;
            if (sorted.stream().filter(window -> window.weekday() == day).count() > 4)
                throw BusinessException.invalid("Use até quatro intervalos por dia.");
        }
        for (var window : sorted) {
            if (!grid(window.opensAt()) || !grid(window.closesAt()) || !window.opensAt().isBefore(window.closesAt()))
                throw BusinessException.invalid("Cada intervalo deve começar antes de terminar, no mesmo dia e em múltiplos de 15 minutos.");
            if (!result.isEmpty()) {
                var previous = result.getLast();
                if (previous.weekday() == window.weekday()) {
                    if (window.opensAt().isBefore(previous.closesAt()))
                        throw BusinessException.invalid("Os intervalos de um mesmo dia não podem se sobrepor.");
                    if (window.opensAt().equals(previous.closesAt())) {
                        result.set(result.size() - 1, new WeeklyWindow(previous.weekday(), previous.opensAt(), window.closesAt()));
                        continue;
                    }
                }
            }
            result.add(window);
        }
        return List.copyOf(result);
    }

    private boolean grid(LocalTime value) {
        return value.getSecond() == 0 && value.getNano() == 0 && value.getMinute() % 15 == 0;
    }
}
