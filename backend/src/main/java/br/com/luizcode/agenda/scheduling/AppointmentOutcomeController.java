package br.com.luizcode.agenda.scheduling;

import br.com.luizcode.agenda.identity.AgendaUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/appointments/{id}")
public class AppointmentOutcomeController {
    private final AppointmentOutcomeService service;
    public AppointmentOutcomeController(AppointmentOutcomeService service){this.service=service;}
    @GetMapping("/history")
    AppointmentHistory.Page history(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID id,@RequestParam(required=false) @Min(0) Integer beforeVersion){
        return service.history(user.businessId(),id,beforeVersion);
    }
    @PostMapping("/outcome")
    ScheduleModels.AppointmentView outcome(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID id,@Valid @RequestBody AppointmentOutcomeService.Change change){
        return service.change(user,id,change);
    }
}
