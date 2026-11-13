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
    OutgoingMail mail(Instant now) { return new OutgoingMail(UUID.randomUUID(),"digest","RESET","test@example.test","Teste","Texto","https://agenda.example/#/redefinir-senha?token=secret",now.plusSeconds(900),UUID.randomUUID()); }
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
    @Test void providerFailuresDistinguishTemporaryFromPermanentAndBoundRetryAfter() {
        assertThat(ResendTransport.classify(202,0).accepted()).isTrue();
        assertThat(ResendTransport.classify(429,999999).retryAfterSeconds()).isEqualTo(86400);
        assertThat(ResendTransport.classify(503,0).retryable()).isTrue();
        assertThat(ResendTransport.classify(401,0).retryable()).isFalse();
        assertThat(ResendTransport.classify(403,0).code()).isEqualTo("AUTH");
        assertThat(ResendTransport.classify(409,0).code()).isEqualTo("CONFLICT");
        assertThat(ResendTransport.classify(422,0).retryable()).isFalse();
    }
}