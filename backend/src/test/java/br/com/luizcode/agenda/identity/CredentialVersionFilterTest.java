package br.com.luizcode.agenda.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CredentialVersionFilterTest {
    @SuppressWarnings("unchecked")
    @Test void canonicalServletPathCannotBypassSessionRevocationWithEncodedRequestUri() throws Exception {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.query(anyString(),any(RowMapper.class),any(UUID.class))).thenReturn(List.of(1));
        var user=new AgendaUser(UUID.randomUUID(),UUID.randomUUID(),"Ana","ana@example.test","unused",0);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            var request=new MockHttpServletRequest("GET","/%61pi/auth/me");
            request.setServletPath("/api/auth/me"); var session=new MockHttpSession(); request.setSession(session);
            var response=new MockHttpServletResponse(); var reached=new AtomicBoolean();
            new CredentialVersionFilter(jdbc).doFilter(request,response,(req,res)->reached.set(true));
            assertThat(response.getStatus()).isEqualTo(401); assertThat(reached).isFalse(); assertThat(session.isInvalid()).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally { SecurityContextHolder.clearContext(); }
    }
}
