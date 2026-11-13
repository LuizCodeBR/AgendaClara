package br.com.luizcode.agenda.identity;

import br.com.luizcode.agenda.identity.AccessModels.OutgoingMail;
import br.com.luizcode.agenda.mail.MailQueueModels.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ResendTransport {
    private final MailSettings settings;
    private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ResendTransport(MailSettings settings,ObjectMapper mapper) { this.settings=settings; this.mapper=mapper; }
    HttpRequest request(OutgoingMail mail) { return request(mail.id(),new Payload(settings.from(),mail.to(),mail.subject(),mail.text(),mail.actionUrl())); }
    HttpRequest request(UUID id,Payload mail) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("from",mail.from()); body.put("to",List.of(mail.to())); body.put("subject",mail.subject());
        body.put("text",mail.text()+(mail.actionUrl().isEmpty()?"":"\n\n"+mail.actionUrl()));
        return HttpRequest.newBuilder(URI.create("https://api.resend.com/emails")).timeout(Duration.ofSeconds(10))
                .header("Authorization","Bearer "+settings.key()).header("Content-Type","application/json")
                .header("Idempotency-Key","agenda-clara/"+id)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
    }
    public Delivery send(UUID id,Payload mail) {
        try {
            var response=http.send(request(id,mail),HttpResponse.BodyHandlers.discarding());
            int after=0;
            try { after=Integer.parseInt(response.headers().firstValue("Retry-After").orElse("0")); } catch(NumberFormatException ignored) {}
            return classify(response.statusCode(),after);
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); return Delivery.failed("INTERRUPTED",true,30); }
        catch(Exception ex) { return Delivery.failed("NETWORK",true,30); }
    }
    static Delivery classify(int status,int after) {
        if(status>=200&&status<300) return Delivery.ok();
        if(status==429) return Delivery.failed("RATE_LIMIT",true,Math.max(30,Math.min(86400,after)));
        if(status==408||status>=500) return Delivery.failed("TEMPORARY",true,30);
        if(status==401||status==403) return Delivery.failed("AUTH",false,0);
        if(status==409) return Delivery.failed("CONFLICT",false,0);
        return Delivery.failed("REJECTED",false,0);
    }
}
