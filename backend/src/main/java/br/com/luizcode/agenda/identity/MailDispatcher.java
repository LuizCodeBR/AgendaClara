package br.com.luizcode.agenda.identity;

import static br.com.luizcode.agenda.identity.AccessModels.*;
import br.com.luizcode.agenda.mail.*;
import br.com.luizcode.agenda.mail.MailQueueModels.*;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public class MailDispatcher {
    @Configuration @EnableScheduling static class Scheduling {}
    private final MailSettings settings;
    private final ResendTransport transport;
    private final Clock clock;
    private final MailOutboxRepository outbox;
    private final MailPayloadCipher cipher;
    private final boolean enabled;
    private final UUID instance=UUID.randomUUID();
    private Instant maintained=Instant.MIN,checked=Instant.MIN;
    public MailDispatcher(MailSettings settings,ResendTransport transport,Clock clock,MailOutboxRepository outbox,
            MailPayloadCipher cipher,@Value("${app.mail.worker-enabled:true}") boolean enabled) {
        this.settings=settings; this.transport=transport; this.clock=clock; this.outbox=outbox; this.cipher=cipher; this.enabled=enabled;
    }
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void enqueue(OutgoingMail mail) { outbox.record(mail,settings.mode(),settings.from(),cipher); }
    public List<Preview> preview(String digest) {
        if(!settings.demo()) return List.of();
        return outbox.previews(digest).stream().map(job->{
            Payload payload=cipher.decrypt(job.aad(),job.payload());
            return new Preview(job.id(),job.purpose(),payload.to(),payload.subject(),payload.text(),payload.actionUrl(),job.expiresAt());
        }).toList();
    }
    @Scheduled(fixedDelay=1000)
    public void dispatchPending() {
        if(!enabled) return;
        try {
            Instant now=clock.instant();
            if(maintained.plusSeconds(30).isBefore(now)) { outbox.maintain(); maintained=now; }
            if(settings.mode().equals("resend")) for(int i=0;i<10;i++) {
                var next=outbox.claim("resend"); if(next.isEmpty()) break;
                Job job=next.get(); Delivery result;
                try { Payload payload=cipher.decrypt(job.aad(),job.payload()); result=transport.send(job.id(),payload); }
                catch(IllegalStateException ex) { result=Delivery.failed("ENCRYPTION",false,0); }
                outbox.finish(job,result);
                if(!result.accepted()) LoggerFactory.getLogger(MailDispatcher.class).warn("Fila de e-mails: tentativa concluída sem aceitação; motivo={}.",result.code());
            }
            if(checked.plusSeconds(15).isBefore(now)) { outbox.heartbeat(instance,settings.mode(),null); checked=now; }
        } catch(RuntimeException ex) {
            LoggerFactory.getLogger(MailDispatcher.class).error("Fila de e-mails: processamento interrompido. Consulte o monitor e a configuração.");
            try { outbox.heartbeat(instance,settings.mode(),"INTERNAL"); } catch(RuntimeException ignored) { /* Next poll retries; leases remain durable. */ }
        }
    }
}
