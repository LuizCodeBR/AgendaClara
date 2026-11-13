package br.com.luizcode.agenda.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class MailSettings {
    private final String mode, baseUrl, from, key;
    private final boolean secureCookie;
    public MailSettings(@Value("${app.mail.mode:disabled}") String mode,
            @Value("${app.mail.base-url:}") String baseUrl,@Value("${app.mail.from:}") String from,
            @Value("${app.mail.resend-key:}") String key,@Value("${app.demo.enabled:false}") boolean demo,
            @Value("${app.demo.public:false}") boolean publicDemo,@Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        if(!Set.of("disabled","demo","resend").contains(mode)) throw new IllegalArgumentException("MAIL_MODE inválido.");
        if(mode.equals("demo")&&!demo) throw new IllegalArgumentException("Caixa de teste exige ambiente de demonstração.");
        if(!baseUrl.isBlank()) {
            URI uri;
            try { uri=URI.create(baseUrl); } catch(IllegalArgumentException ex) { throw new IllegalArgumentException("APP_BASE_URL inválida."); }
            boolean loopback=Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost()==null?"":uri.getHost());
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null
                    || !(uri.getPath().isEmpty()||uri.getPath().equals("/"))
                    || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback)))
                throw new IllegalArgumentException("APP_BASE_URL precisa ser uma origem HTTPS; HTTP somente local.");
        }
        if(!mode.equals("disabled") && (publicDemo || mode.equals("resend")) && baseUrl.isBlank())
            throw new IllegalArgumentException("Configure APP_BASE_URL antes de habilitar envio público.");
        if(mode.equals("resend") && (key.isBlank()||from.isBlank()||from.contains("\n")||from.contains("\r")))
            throw new IllegalArgumentException("Configure RESEND_API_KEY e MAIL_FROM para envio real.");
        this.mode=mode; this.baseUrl=baseUrl.replaceAll("/$",""); this.from=from; this.key=key; this.secureCookie=secureCookie;
    }
    public String mode() { return mode; }
    public boolean demo() { return mode.equals("demo"); }
    public boolean available() { return !mode.equals("disabled"); }
    public boolean secureCookie() { return secureCookie; }
    String from() { return from; }
    String key() { return key; }
    public String origin(HttpServletRequest request) {
        if(!baseUrl.isBlank()) return baseUrl;
        // Local demo only. Never build a public recovery link from Host/forwarded headers.
        String host=Set.of("localhost","127.0.0.1","[::1]").contains(request.getServerName())?request.getServerName():"localhost";
        return "http://"+host+":"+request.getLocalPort();
    }
    @Override public String toString() { return "MailSettings[mode="+mode+"]"; }
}
