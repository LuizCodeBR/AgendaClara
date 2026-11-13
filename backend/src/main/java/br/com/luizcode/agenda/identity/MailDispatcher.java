package br.com.luizcode.agenda.identity;

import static br.com.luizcode.agenda.identity.AccessModels.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MailDispatcher {
    @Configuration @EnableScheduling static class Scheduling {}
    private record Pending(OutgoingMail mail,int attempts,Instant nextAttempt) {}
    private final MailSettings settings;
    private final ResendTransport transport;
    private final Clock clock;
    private final LinkedHashMap<String,List<Preview>> previews=new LinkedHashMap<>();
    private final ArrayBlockingQueue<Pending> queue=new ArrayBlockingQueue<>(100);
    public MailDispatcher(MailSettings settings,ResendTransport transport,Clock clock) {
        this.settings=settings; this.transport=transport; this.clock=clock;
    }
    @TransactionalEventListener
    public void committed(OutgoingMail mail) {
        if(settings.demo()) {
            synchronized(previews) {
                purge();
                if(!previews.containsKey(mail.mailboxDigest()) && previews.size()>=128)
                    previews.remove(previews.keySet().iterator().next());
                var items=new ArrayList<>(previews.getOrDefault(mail.mailboxDigest(),List.of()));
                items.addFirst(mail.preview()); previews.put(mail.mailboxDigest(),List.copyOf(items.subList(0,Math.min(5,items.size()))));
            }
        } else if(settings.mode().equals("resend")&&!queue.offer(new Pending(mail,0,clock.instant()))) {
            warn();
        }
    }
    public List<Preview> preview(String digest) {
        synchronized(previews) { purge(); return previews.getOrDefault(digest,List.of()); }
    }
    private void purge() {
        Instant now=clock.instant();
        previews.replaceAll((key,items)->items.stream().filter(item->item.expiresAt().isAfter(now)).toList());
        previews.values().removeIf(List::isEmpty);
    }
    @Scheduled(fixedDelay=1000)
    public void dispatchPending() {
        for(int i=queue.size();i>0;i--) {
            Pending pending=queue.poll(); if(pending==null) return;
            Instant now=clock.instant();
            if(!pending.mail().expiresAt().isAfter(now)) continue;
            if(pending.nextAttempt().isAfter(now)) { queue.offer(pending); continue; }
            if(transport.send(pending.mail())) continue;
            int attempts=pending.attempts()+1;
            if(attempts<3) queue.offer(new Pending(pending.mail(),attempts,now.plusSeconds(30L*attempts)));
            else warn();
        }
    }
    private static void warn() {
        LoggerFactory.getLogger(MailDispatcher.class).warn("Envio de e-mail não concluído. Confira a configuração do remetente; o usuário pode solicitar outro link.");
    }
}
