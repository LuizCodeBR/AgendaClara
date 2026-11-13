package br.com.luizcode.agenda.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import br.com.luizcode.agenda.mail.*;
import br.com.luizcode.agenda.mail.MailQueueModels.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MailDispatcherTest {
    @Test void slowHealthyBatchContinuesToPublishFreshHeartbeatBeforeItLooksStopped() {
        var time=new AtomicReference<>(Instant.parse("2030-06-03T10:00:00Z"));
        Clock clock=mock(Clock.class); when(clock.instant()).thenAnswer(call->time.get());
        var lastHeartbeat=new AtomicReference<>(time.get());
        var outbox=mock(MailOutboxRepository.class); var cipher=mock(MailPayloadCipher.class); var transport=mock(ResendTransport.class);
        var messages=new ArrayDeque<Job>();
        for(int i=0;i<10;i++) messages.add(new Job(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"VERIFY","resend","PROCESSING","ciphertext",1,0,
                time.get(),time.get().plusSeconds(86400),time.get().plusSeconds(82800),null,time.get().plusSeconds(60),UUID.randomUUID(),null,null));
        when(outbox.claim("resend")).thenAnswer(call->Optional.ofNullable(messages.poll()));
        doAnswer(call->{lastHeartbeat.set(time.get()); return null;}).when(outbox).heartbeat(any(),eq("resend"),isNull());
        when(cipher.decrypt(anyString(),anyString())).thenReturn(new Payload("from@example.test","to@example.test","Teste","Texto",""));
        when(transport.send(any(),any())).thenAnswer(call->{
            time.updateAndGet(now->now.plusSeconds(10));
            assertThat(Duration.between(lastHeartbeat.get(),time.get())).isLessThan(Duration.ofSeconds(90));
            return Delivery.ok();
        });
        var settings=new MailSettings("resend","https://example.test","from@example.test","fake",false,false,true);
        new MailDispatcher(settings,transport,clock,outbox,cipher,true).dispatchPending();
        verify(transport,times(10)).send(any(),any());
        assertThat(lastHeartbeat.get()).isAfter(time.get().minusSeconds(30));
    }
}
