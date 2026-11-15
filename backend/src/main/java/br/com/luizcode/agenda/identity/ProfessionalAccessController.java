package br.com.luizcode.agenda.identity;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import br.com.luizcode.agenda.shared.BusinessException;

@RestController
public class ProfessionalAccessController {
    private void context(AgendaUser user,java.util.UUID expected){
        if(!user.id().equals(expected))throw new BusinessException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,"A conta ativa mudou. Entre novamente.");
    }
    private final ProfessionalAccessService service;
    private final AccountAccessController accounts;
    public ProfessionalAccessController(ProfessionalAccessService service,AccountAccessController accounts){this.service=service;this.accounts=accounts;}
    @GetMapping("/api/admin/professional-access")
    List<ProfessionalAccessService.State> list(@AuthenticationPrincipal AgendaUser user,@RequestHeader("X-Agenda-Account") UUID expected){context(user,expected);return service.list(user.businessId());}
    @PostMapping("/api/admin/professional-access/{id}/invite")
    ProfessionalAccessService.Issued invite(@AuthenticationPrincipal AgendaUser user,@RequestHeader("X-Agenda-Account") UUID expected,@PathVariable UUID id,@Valid @RequestBody ProfessionalAccessService.Invite request){
        context(user,expected);return service.invite(user.businessId(),id,request);
    }
    @PostMapping("/api/admin/professional-access/{id}/revoke")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal AgendaUser user,@RequestHeader("X-Agenda-Account") UUID expected,@PathVariable UUID id,@Valid @RequestBody ProfessionalAccessService.Version request){
        context(user,expected);service.revoke(user.businessId(),id,request.version());return ResponseEntity.noContent().build();
    }
    @PostMapping("/api/auth/professional-invitations/preview")
    ProfessionalAccessService.Preview preview(@Valid @RequestBody ProfessionalAccessService.Token request){return service.preview(request.token());}
    @PostMapping("/api/auth/professional-invitations/accept")
    ResponseEntity<Void> accept(@AuthenticationPrincipal AgendaUser user,@Valid @RequestBody ProfessionalAccessService.Accept request,jakarta.servlet.http.HttpServletResponse response){
        if(user!=null)throw BusinessException.conflict("Saia da conta atual antes de ativar outro acesso.");
        var accepted=service.accept(request);accounts.professionalActivated(accepted.mailboxProof(),response);return ResponseEntity.noContent().build();
    }
}
