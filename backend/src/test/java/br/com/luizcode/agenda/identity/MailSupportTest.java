package br.com.luizcode.agenda.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.luizcode.agenda.identity.AccessModels.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MailSupportTest {
    MailSettings demo() { return new MailSettings("demo","","","",true,false,false); }
    OutgoingMail mail(Instant now) { return new OutgoingMail(UUID.randomUUID(),"digest","RESET","test@example.test","Teste","Texto","https://agenda.example/#/redefinir-senha?token=secret",now.plusSeconds(900)); }
    @Test void publicOriginsCannotBeControlledThroughHostAndRealModeRequiresCredentials() {
        for(String origin:List.of("http://evil.example","https://user@agenda.example","https://agenda.example/path","https://agenda.example?query=1","https://agenda.example/#token","//agenda.example"))
            assertThatThrownBy(()->new MailSettings("resend",origin,"from@example.test","test-secret",false,false,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MailSettings("demo","","","",false,false,false)).hasMessageContaining("demonstração");
        assertThatThrownBy(()->new MailSettings("resend","https://agenda.example","","",false,false,true)).hasMessageContaining("RESEND_API_KEY");
        assertThatThrownBy(()->new MailSettings("demo","","","",true,true,true)).hasMessageContaining("APP_BASE_URL");
        var request=new org.springframework.mock.web.MockHttpServletRequest(); request.setServerName("poison.example"); request.setLocalPort(8099);
        assertThat(demo().origin(request)).isEqualTo("http://localhost:8099");
        var fixed=new MailSettings("resend","https://agenda.example/","from@example.test","test-secret",false,false,true);
        assertThat(fixed.origin(request)).isEqualTo("https://agenda.example");
        assertThat(fixed.toString()).doesNotContain("test-secret");
    }
    @Test void realProviderRequestUsesFixedHttpsEndpointAndStableIdempotencyKey() {
        var settings=new MailSettings("resend","https://agenda.example","from@example.test","test-secret",false,false,true);
        var transport=new ResendTransport(settings,JsonMapper.builder().build()); var mail=mail(Instant.now());
        var request=transport.request(mail);
        assertThat(request.uri().toString()).isEqualTo("https://api.resend.com/emails");
        assertThat(request.headers().firstValue("Idempotency-Key")).contains("agenda-clara/"+mail.id());
        assertThat(request.timeout()).contains(Duration.ofSeconds(10));
        assertThat(mail.toString()).doesNotContain("token=secret","test@example.test");
        assertThat(new ResetRequest("secret","password").toString()).doesNotContain("secret","password");
    }
    @Test void previewsAreBoundedAndExpireAndNeverCallTheExternalProvider() {
        var now=Instant.parse("2030-06-03T10:00:00Z"); var clock=new MutableClock(now); var transport=mock(ResendTransport.class);
        var dispatcher=new MailDispatcher(demo(),transport,clock);
        for(int i=0;i<8;i++) dispatcher.committed(mail(now));
        assertThat(dispatcher.preview("digest")).hasSize(5); assertThat(dispatcher.preview("other")).isEmpty();
        clock.now=now.plusSeconds(901); assertThat(dispatcher.preview("digest")).isEmpty(); verifyNoInteractions(transport);
    }
    @Test void externalDeliveryRetriesAtMostThreeTimesWithTheSameMessage() {
        var now=Instant.parse("2030-06-03T10:00:00Z"); var clock=new MutableClock(now); var transport=mock(ResendTransport.class);
        var settings=new MailSettings("resend","https://agenda.example","from@example.test","test-secret",false,false,true);
        var dispatcher=new MailDispatcher(settings,transport,clock); var mail=mail(now);
        when(transport.send(mail)).thenReturn(false,false,true);
        dispatcher.committed(mail); dispatcher.dispatchPending(); verify(transport,times(1)).send(mail);
        dispatcher.dispatchPending(); verify(transport,times(1)).send(mail);
        clock.now=now.plusSeconds(30); dispatcher.dispatchPending();
        clock.now=now.plusSeconds(90); dispatcher.dispatchPending(); dispatcher.dispatchPending();
        verify(transport,times(3)).send(mail); assertThat(dispatcher.preview("digest")).isEmpty();
    }
    static class MutableClock extends Clock {
        Instant now; MutableClock(Instant now) {this.now=now;}
        public ZoneId getZone() {return ZoneOffset.UTC;} public Clock withZone(ZoneId zone) {return this;} public Instant instant() {return now;}
    }
}
