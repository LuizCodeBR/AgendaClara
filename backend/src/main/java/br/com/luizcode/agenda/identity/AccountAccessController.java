package br.com.luizcode.agenda.identity;

import static br.com.luizcode.agenda.identity.AccessModels.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountAccessController {
    public static final String COOKIE="AGENDA_DEMO_MAILBOX";
    private static final Notice ACCEPTED=new Notice("Se houver uma conta elegível, o link será enviado. Confira a caixa de entrada e o spam. Aguarde um minuto antes de solicitar outro.");
    private final AccountAccessService access;
    private final MailDispatcher mails;
    private final MailSettings settings;
    public AccountAccessController(AccountAccessService access,MailDispatcher mails,MailSettings settings) {
        this.access=access; this.mails=mails; this.settings=settings;
    }
    @PostMapping("/api/auth/email/request")
    ResponseEntity<Notice> verification(@Valid @RequestBody EmailRequest body,HttpServletRequest request) {
        access.request(body.email(),"VERIFY",proof(request),settings.origin(request));
        return ResponseEntity.accepted().body(ACCEPTED);
    }
    @PostMapping("/api/auth/password/request")
    ResponseEntity<Notice> recovery(@Valid @RequestBody EmailRequest body,HttpServletRequest request) {
        access.request(body.email(),"RESET",proof(request),settings.origin(request));
        return ResponseEntity.accepted().body(ACCEPTED);
    }
    @PostMapping("/api/auth/email/confirm")
    Notice verify(@Valid @RequestBody TokenRequest body) {
        access.verify(body.token()); return new Notice("E-mail confirmado. Você já pode publicar sua página quando o atendimento estiver configurado.");
    }
    @PostMapping("/api/auth/password/reset")
    Notice reset(@Valid @RequestBody ResetRequest body) {
        access.reset(body.token(),body.password());
        return new Notice("Senha alterada. Entre novamente com a nova senha. As sessões anteriores perderam o acesso.");
    }
    @GetMapping("/api/auth/demo-mailbox")
    List<Preview> mailbox(HttpServletRequest request) {
        String digest=access.mailboxDigest(proof(request));
        return digest==null?List.of():mails.preview(digest);
    }
    @PostMapping("/api/auth/demo-mailbox/open")
    ResponseEntity<Void> open(@AuthenticationPrincipal AgendaUser user,HttpServletRequest request,HttpServletResponse response) {
        if(user==null) return ResponseEntity.status(401).build();
        attach(access.openDemoMailbox(user.email(),proof(request)),response);
        return ResponseEntity.noContent().build();
    }
    void registered(String email,HttpServletRequest request,HttpServletResponse response) {
        String proof=access.openDemoMailbox(email,proof(request));
        attach(proof,response);
        access.request(email,"VERIFY",proof,settings.origin(request));
    }
    private void attach(String proof,HttpServletResponse response) {
        if(proof==null) return;
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,proof).httpOnly(true).secure(settings.secureCookie())
                .sameSite("Strict").path("/api/auth").maxAge(3600).build().toString());
    }
    private static String proof(HttpServletRequest request) {
        if(request.getCookies()!=null) for(Cookie cookie:request.getCookies()) if(cookie.getName().equals(COOKIE)) return cookie.getValue();
        return null;
    }
}
