package br.com.luizcode.agenda.identity;

import static br.com.luizcode.agenda.identity.RegistrationModels.*;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class RegistrationController {
    private final RegistrationService registration;
    public RegistrationController(RegistrationService registration) { this.registration=registration; }

    @PostMapping("/api/auth/register")
    ResponseEntity<RegistrationReceipt> register(@AuthenticationPrincipal AgendaUser user, @Valid @RequestBody RegisterRequest request) {
        if (user!=null) throw RegistrationService.conflict();
        try {
            var result=registration.register(request);
            return ResponseEntity.status(result.created()?201:200).body(result.receipt());
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.ok(registration.replay(request));
        }
    }
}
