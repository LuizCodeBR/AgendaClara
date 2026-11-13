package br.com.luizcode.agenda.mail;

import br.com.luizcode.agenda.identity.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/emails")
public class MailQueueController {
    private final MailOutboxRepository outbox;
    private final MailSettings settings;
    public MailQueueController(MailOutboxRepository outbox,MailSettings settings) { this.outbox=outbox; this.settings=settings; }
    @GetMapping
    MailQueueModels.Monitor monitor(@AuthenticationPrincipal AgendaUser user,@RequestParam(required=false) String status) {
        if(status!=null&&!Set.of("PENDING","PROCESSING","ACCEPTED","FAILED","EXPIRED","CANCELLED").contains(status))
            throw BusinessException.invalid("Filtro de envio inválido.");
        return outbox.monitor(user.businessId(),settings.mode(),status);
    }
    @PostMapping("/{id}/retry")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void retry(@AuthenticationPrincipal AgendaUser user,@PathVariable UUID id) { outbox.retry(user.businessId(),id,settings.mode()); }
}
