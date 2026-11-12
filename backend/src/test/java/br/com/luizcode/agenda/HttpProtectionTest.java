package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes=AgendaApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpProtectionTest {
    @LocalServerPort int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private HttpResponse<String> chunked(String path,String type,String body) throws Exception {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .header("Content-Type",type).POST(HttpRequest.BodyPublishers.ofInputStream(()->new ByteArrayInputStream(bytes))).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }

    @Test void embeddedServerRejectsOversizedChunkedJsonBeforeCsrf() throws Exception {
        var response=chunked("/api/public/test/bookings","application/json","x".repeat(17000));
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("tamanho permitido");
    }

    @Test void embeddedTomcatRejectsOversizedChunkedFormWithoutAuthenticating() throws Exception {
        var response=chunked("/api/auth/login","application/x-www-form-urlencoded",
                "username=marina%40demo.agendaclara.dev&password=Demo%4012345&padding="+"x".repeat(17000));
        assertThat(response.statusCode()).isBetween(400,499);
        assertThat(response.headers().allValues("Set-Cookie")).noneMatch(cookie->cookie.contains("JSESSIONID"));
    }

    @Test void csrfCookieIsHttpOnlySameSiteAndResponsesHaveSecurityHeaders() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/csrf")).GET().build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Set-Cookie")).anySatisfy(cookie-> {
            assertThat(cookie).contains("JSESSIONID=","HttpOnly","SameSite=Lax");
        });
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.headers().firstValue("Content-Security-Policy").orElse("")).contains("script-src 'self'","frame-ancestors 'none'");
    }
}
