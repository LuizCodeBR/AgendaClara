package br.com.luizcode.agenda.identity;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RequestGuardFilterTest {
    private RequestGuardFilter filter(int api, int login, int account, int booking, int write) {
        return new RequestGuardFilter(new RequestLimits(() -> 0, 100), api, login, account, booking, write);
    }
    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
    private MockHttpServletResponse execute(RequestGuardFilter filter, MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req,res) -> ((jakarta.servlet.http.HttpServletResponse)res).setStatus(204));
        return response;
    }

    @Test void rejectsDeclaredOversizeBeforePassingToApplication() throws Exception {
        var request=request("POST", "/api/public/test/bookings");
        request.setContentType("application/json");
        request.setContent(new byte[RequestGuardFilter.MAX_BODY+1]);
        var response=execute(filter(100,100,100,100,100),request);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test void countsActualBytesWhenContentLengthIsUnknown() throws Exception {
        var request=new MockHttpServletRequest("POST", "/api/public/test/bookings") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContentType("application/problem+json");
        request.setContent("á".repeat(RequestGuardFilter.MAX_BODY).getBytes(StandardCharsets.UTF_8));
        assertThat(execute(filter(100,100,100,100,100),request).getStatus()).isEqualTo(413);
    }

    @Test void exactLimitReachesApplicationWithoutLosingBytes() throws Exception {
        var request=request("POST", "/api/public/test/bookings");
        byte[] bytes="a".repeat(RequestGuardFilter.MAX_BODY).getBytes(StandardCharsets.UTF_8);
        request.setContentType("application/json");
        request.setContent(bytes);
        var called=new AtomicBoolean();
        filter(100,100,100,100,100).doFilter(request,new MockHttpServletResponse(),(req,res)-> {
            assertThat(req.getInputStream().readAllBytes()).isEqualTo(bytes); called.set(true);
        });
        assertThat(called).isTrue();
    }

    @Test void freshCookiesAndSpoofedForwardingHeadersCannotBypassGlobalBookingQuota() throws Exception {
        var filter=filter(100,100,100,2,100);
        for(int i=0;i<3;i++) {
            var request=request("POST", "/api/public/business-"+i+"/bookings");
            request.setCookies(new Cookie("JSESSIONID","new-"+i));
            request.addHeader("X-Forwarded-For","192.0.2."+i);
            request.addHeader("Forwarded","for=192.0.2."+i);
            var response=execute(filter,request);
            assertThat(response.getStatus()).isEqualTo(i<2 ? 204:429);
            if(i==2) assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        }
    }

    @Test void globalLoginQuotaAlsoAppliesToDifferentAccounts() throws Exception {
        var filter=filter(100,2,100,100,100);
        for(int i=0;i<3;i++) {
            var request=request("POST","/api/auth/login");
            request.addParameter("username","user"+i+"@example.com");
            request.addParameter("password","wrong");
            assertThat(execute(filter,request).getStatus()).isEqualTo(i<2?204:429);
        }
    }

    @Test void accountQuotaNormalizesEmailAndDoesNotDependOnSession() throws Exception {
        var filter=filter(100,100,2,100,100);
        for(String name: new String[]{" TEST@example.com ","test@EXAMPLE.COM","test@example.com"}) {
            var request=request("POST","/api/auth/login");
            request.addParameter("username",name); request.addParameter("password","wrong");
            var response=execute(filter,request);
            assertThat(response.getStatus()).isEqualTo(name.equals("test@example.com")?429:204);
        }
    }

    @Test void passwordBoundCountsUtf8Bytes() throws Exception {
        var request=request("POST","/api/auth/login");
        request.addParameter("username","test@example.com");
        request.addParameter("password","á".repeat(37));
        assertThat(execute(filter(100,100,100,100,100),request).getStatus()).isEqualTo(401);
    }

    @Test void csrfEndpointIsLimitedWithoutAllocatingSessionsOnRejectedRequests() throws Exception {
        var filter=filter(1,100,100,100,100);
        assertThat(execute(filter,request("GET","/api/auth/csrf")).getStatus()).isEqualTo(204);
        var rejected=request("GET","/api/auth/csrf");
        assertThat(execute(filter,rejected).getStatus()).isEqualTo(429);
        assertThat(rejected.getSession(false)).isNull();
    }

    @Test void writeQuotaIsSharedAcrossAdministrativeRoutesButAllowsReads() throws Exception {
        var filter=filter(100,100,100,100,1);
        assertThat(execute(filter,request("POST","/api/admin/services")).getStatus()).isEqualTo(204);
        assertThat(execute(filter,request("DELETE","/api/admin/blocks/id")).getStatus()).isEqualTo(429);
        assertThat(execute(filter,request("GET","/api/admin/agenda")).getStatus()).isEqualTo(204);
    }
}
