package br.com.luizcode.agenda.identity;

import br.com.luizcode.agenda.identity.AccessModels.OutgoingMail;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ResendTransport {
    private final MailSettings settings;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ResendTransport(MailSettings settings,ObjectMapper mapper) { this.settings=settings; this.mapper=mapper; }
    HttpRequest request(OutgoingMail mail) {
        return HttpRequest.newBuilder(URI.create("https://api.resend.com/emails")).timeout(Duration.ofSeconds(10))
                .header("Authorization","Bearer "+settings.key()).header("Content-Type","application/json")
                .header("Idempotency-Key","agenda-clara/"+mail.id())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                        "from",settings.from(),"to",List.of(mail.to()),"subject",mail.subject(),
                        "text",mail.text()+(mail.actionUrl().isEmpty()?"":"\n\n"+mail.actionUrl()))))).build();
    }
    public boolean send(OutgoingMail mail) {
        try { int status=http.send(request(mail),HttpResponse.BodyHandlers.discarding()).statusCode(); return status>=200&&status<300; }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
        catch(Exception ex) { return false; } // Never log provider response, credentials, recipients or links.
    }
}
