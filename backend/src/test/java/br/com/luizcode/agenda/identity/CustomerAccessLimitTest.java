package br.com.luizcode.agenda.identity;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
class CustomerAccessLimitTest {
    @Test void changingCookiesOrForwardedHeadersCannotBypassTheAccessBudget() throws Exception{
        var filter=new RequestGuardFilter(new RequestLimits(),1000,1000,1000,1000,1000,1000,2);
        for(int i=0;i<3;i++){
            var request=new MockHttpServletRequest("POST","/api/customer/session");
            request.setServletPath("/api/customer/session");request.addHeader("X-Forwarded-For","192.0.2."+i);
            request.setCookies(new jakarta.servlet.http.Cookie("random",Integer.toString(i)));
            request.setContentType("application/json");request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var response=new MockHttpServletResponse();
            filter.doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(204));
            assertThat(response.getStatus()).isEqualTo(i<2?204:429);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            if(i==2)assertThat(response.getHeader("Retry-After")).isNotBlank();
        }
    }
}
