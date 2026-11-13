package br.com.luizcode.agenda.catalog;

import br.com.luizcode.agenda.catalog.BusinessSetupService.*;
import br.com.luizcode.agenda.identity.AgendaUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class BusinessSetupController {
    private final BusinessSetupService setup;
    public BusinessSetupController(BusinessSetupService setup) { this.setup=setup; }
    @GetMapping("/api/admin/business")
    Setup get(@AuthenticationPrincipal AgendaUser user) { return setup.get(user.businessId()); }
    @PutMapping("/api/admin/business/publication")
    Setup publish(@AuthenticationPrincipal AgendaUser user,@Valid @RequestBody PublicationRequest request) {
        return setup.publish(user.businessId(),request);
    }
}
