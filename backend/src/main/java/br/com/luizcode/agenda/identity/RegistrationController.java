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
    private final AccountAccessController access;
    public RegistrationController(RegistrationService registration,AccountAccessController access) { this.registration=registration; this.access=access; }

    @PostMapping("/api/auth/register")
    ResponseEntity<RegistrationReceipt> register(@AuthenticationPrincipal AgendaUser user, @Valid @RequestBody RegisterRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest,jakarta.servlet.http.HttpServletResponse response) {
        if (user!=null) throw RegistrationService.conflict();
        try {
            var result=registration.register(request);
            access.registered(result.receipt().email(),httpRequest,response);
            return ResponseEntity.status(result.created()?201:200).body(result.receipt());
        } catch (DataIntegrityViolationException ex) {
            var receipt=registration.replay(request);
            access.registered(receipt.email(),httpRequest,response);
            return ResponseEntity.ok(receipt);
        }
    }
}
