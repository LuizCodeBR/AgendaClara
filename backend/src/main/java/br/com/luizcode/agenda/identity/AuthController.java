package br.com.luizcode.agenda.identity;

import br.com.luizcode.agenda.catalog.CatalogModels.Business;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final CatalogRepository catalog;

    public AuthController(CatalogRepository catalog) { this.catalog = catalog; }

    @GetMapping("/api/auth/csrf")
    ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    public record CurrentUser(java.util.UUID id,String name,String email,String role,java.util.UUID professionalId,Business business) {}

    @GetMapping("/api/auth/me")
    ResponseEntity<CurrentUser> me(@AuthenticationPrincipal AgendaUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CurrentUser(user.id(),user.name(),user.email(),user.role(),user.professionalId(),catalog.business(user.businessId())));
    }
}
