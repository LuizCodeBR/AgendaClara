package br.com.luizcode.agenda.identity;

import br.com.luizcode.agenda.scheduling.AppointmentOutcomeService;
import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/professional")
public class ProfessionalAgendaController {
    private final ProfessionalAgendaService service;
    public ProfessionalAgendaController(ProfessionalAgendaService service){this.service=service;}
    private void context(AgendaUser user,UUID expected){
        if(!user.id().equals(expected))throw new BusinessException(HttpStatus.PRECONDITION_FAILED,"A conta ativa mudou. Entre novamente para consultar sua agenda.");
    }
    @GetMapping("/agenda")
    ProfessionalAgendaService.Agenda day(@AuthenticationPrincipal AgendaUser user,@RequestHeader("X-Agenda-Account") UUID expected,@RequestParam LocalDate date){
        context(user,expected);return service.day(user,date);
    }
    @GetMapping("/appointments/{id}/history")
    ProfessionalAgendaService.History history(@AuthenticationPrincipal AgendaUser user,@RequestHeader("X-Agenda-Account") UUID expected,@PathVariable UUID id,@RequestParam(required=false) @Min(0) Integer beforeVersion){
        context(user,expected);return service.history(user,id,beforeVersion);
    }
    @PostMapping("/appointments/{id}/outcome")
    ProfessionalAgendaService.Item outcome(@AuthenticationPrincipal AgendaUser user,@RequestHeader("X-Agenda-Account") UUID expected,@PathVariable UUID id,@Valid @RequestBody AppointmentOutcomeService.Change request){
        context(user,expected);return service.change(user,id,request);
    }
}
