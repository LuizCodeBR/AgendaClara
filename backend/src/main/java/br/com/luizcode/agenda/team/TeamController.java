package br.com.luizcode.agenda.team;

import static br.com.luizcode.agenda.team.TeamModels.*;
import br.com.luizcode.agenda.identity.AgendaUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/professionals")
public class TeamController {
    private final TeamService team;
    public TeamController(TeamService team) { this.team = team; }

    @GetMapping
    List<ProfessionalConfiguration> list(@AuthenticationPrincipal AgendaUser user) {
        return team.list(user.businessId());
    }

    @PostMapping
    ResponseEntity<ProfessionalConfiguration> create(@AuthenticationPrincipal AgendaUser user,
                                                     @Valid @RequestBody CreateProfessional request) {
        return ResponseEntity.status(201).body(team.create(user.businessId(), request));
    }

    @PutMapping("/{id}")
    ProfessionalConfiguration update(@AuthenticationPrincipal AgendaUser user, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateProfessional request) {
        return team.update(user.businessId(), id, request);
    }
}
