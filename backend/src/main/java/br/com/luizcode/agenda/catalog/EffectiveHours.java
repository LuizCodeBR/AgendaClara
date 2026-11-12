package br.com.luizcode.agenda.catalog;

import br.com.luizcode.agenda.hours.DateHoursRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** A date override replaces the weekly windows; an empty override means closed. */
@Service
public class EffectiveHours {
    private final CatalogRepository catalog;
    private final DateHoursRepository dates;
    public EffectiveHours(CatalogRepository catalog, DateHoursRepository dates) { this.catalog=catalog; this.dates=dates; }

    public List<WorkingWindow> onDate(UUID businessId, UUID professionalId, LocalDate date) {
        var special=dates.between(businessId,professionalId,date,date).get(date);
        return special==null ? catalog.hours(businessId,professionalId,date.getDayOfWeek().getValue()) : special.hours();
    }
    public Map<LocalDate,DateHoursRepository.DateHours> exceptions(UUID businessId, UUID professionalId, LocalDate first, LocalDate last) {
        return dates.between(businessId,professionalId,first,last);
    }
}
