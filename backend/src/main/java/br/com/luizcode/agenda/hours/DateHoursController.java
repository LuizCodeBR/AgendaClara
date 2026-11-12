package br.com.luizcode.agenda.hours;

import static br.com.luizcode.agenda.hours.DateHoursModels.*;
import br.com.luizcode.agenda.identity.AgendaUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/professionals/{professionalId}/exceptions")
public class DateHoursController {
    private final DateHoursService dates;
    public DateHoursController(DateHoursService dates) { this.dates=dates; }
    @GetMapping
    ExceptionCalendar list(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID professionalId) {
        return dates.list(user.businessId(),professionalId);
    }
    @PutMapping("/{date}")
    ExceptionCalendar save(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID professionalId,
                           @PathVariable LocalDate date,@Valid @RequestBody SaveException request) {
        return dates.save(user.businessId(),professionalId,date,request);
    }
    @DeleteMapping("/{date}")
    ExceptionCalendar remove(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID professionalId,
                             @PathVariable LocalDate date,@RequestParam @Min(0) int version) {
        return dates.remove(user.businessId(),professionalId,date,version);
    }
}
