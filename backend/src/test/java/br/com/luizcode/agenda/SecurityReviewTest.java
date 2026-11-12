package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.identity.AgendaUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties="app.protection.account-per-minute=3")
class SecurityReviewTest extends ApiTestBase {
    @Test void loginRotatesSessionAndErasesPasswordHashFromAuthenticatedPrincipal() throws Exception {
        var prior = new MockHttpSession();
        String priorId = prior.getId();
        var login=mvc.perform(post("/api/auth/login").session(prior).with(request -> { request.setRequestedSessionId(priorId); request.setRequestedSessionIdValid(true); return request; }).with(csrf())
                .param("username","marina@demo.agendaclara.dev").param("password","Demo@12345"))
                .andExpect(status().isNoContent()).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(priorId);
        var context=(SecurityContext)session.getAttribute("SPRING_SECURITY_CONTEXT");
        var principal=(AgendaUser)context.getAuthentication().getPrincipal();
        assertThat(principal.getPassword()).isNull();
        assertThat(principal.toString()).doesNotContain("Demo@12345","$2","marina@");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test void realAuthenticationIsThrottledAcrossFreshSessionsAndNormalizedNames() throws Exception {
        for(int i=0;i<4;i++) {
            var request=post("/api/auth/login").with(csrf()).session(new MockHttpSession())
                    .header("X-Forwarded-For","192.0.2."+i)
                    .param("username",i%2==0?" LIMIT@example.com ":"limit@EXAMPLE.com").param("password","wrong");
            var result=mvc.perform(request).andExpect(status().is(i<3?401:429));
            if(i==3) result.andExpect(header().exists("Retry-After"))
                    .andExpect(header().string("Cache-Control",containsString("no-store")));
        }
    }

    @Test void oversizedJsonIsRejectedBeforeCsrfAndLeavesDatabaseUntouched() throws Exception {
        mvc.perform(post(BOOKINGS).contentType("application/json").content("x".repeat(16385)))
                .andExpect(status().is(413)).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("Content-Security-Policy",containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options","nosniff"));
        assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isZero();
    }

    @Test void hugePasswordDoesNotCauseServerErrorOrAuthenticate() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                .param("username","marina@demo.agendaclara.dev").param("password","á".repeat(37)))
                .andExpect(status().isUnauthorized());
    }
}
