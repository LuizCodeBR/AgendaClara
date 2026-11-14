package br.com.luizcode.agenda.policy;

import br.com.luizcode.agenda.identity.AgendaUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/business/booking-policy")
public class BookingPolicyController {
    private final BookingPolicyService policies;
    public BookingPolicyController(BookingPolicyService policies){this.policies=policies;}
    @GetMapping BookingPolicy get(@AuthenticationPrincipal AgendaUser user){return policies.get(user.businessId());}
    @PutMapping BookingPolicy save(@AuthenticationPrincipal AgendaUser user,@Valid @RequestBody BookingPolicyService.Save request){return policies.save(user.businessId(),request);}
}
