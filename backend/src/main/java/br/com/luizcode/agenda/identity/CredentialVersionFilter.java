package br.com.luizcode.agenda.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Database stamp revokes existing sessions across processes after password recovery. */
public final class CredentialVersionFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    public CredentialVersionFilter(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        String path=request.getServletPath();
        if(path.isEmpty()) path=request.getRequestURI().substring(request.getContextPath().length());
        if(path.startsWith("/api/")
                && authentication!=null && authentication.getPrincipal() instanceof AgendaUser user) {
            var current=jdbc.query("select credentials_version from app_user where id=?",(rs,row)->rs.getInt(1),user.id());
            if(current.isEmpty()||current.getFirst()!=user.credentialsVersion()) {
                var session=request.getSession(false); if(session!=null) session.invalidate();
                SecurityContextHolder.clearContext();
                response.setStatus(401); response.setContentType("application/problem+json;charset=UTF-8");
                response.setHeader("Cache-Control","no-store");
                response.getWriter().write("{\"status\":401,\"detail\":\"Seu acesso foi atualizado. Entre novamente com sua senha atual.\"}");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
