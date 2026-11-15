package br.com.luizcode.agenda.scheduling;

import br.com.luizcode.agenda.identity.AgendaUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/professionals/{id}")
public class DailySummaryController {
    private final DailySummaryService summaries;
    public DailySummaryController(DailySummaryService summaries){this.summaries=summaries;}
    @GetMapping("/daily-summary")
    DailySummaryService.Summary read(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID id,@RequestParam LocalDate date){
        return summaries.read(user.businessId(),id,date);
    }
    @PutMapping("/whatsapp-contact")
    DailySummaryService.Professional contact(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID id,@Valid @RequestBody DailySummaryService.Contact contact){
        return summaries.saveContact(user.businessId(),id,contact);
    }
}
