package br.com.luizcode.agenda.identity;

import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return email -> jdbc.query("select * from app_user where email = ?", (rs, row) ->
                new AgendaUser(rs.getObject("id", java.util.UUID.class), rs.getObject("business_id", java.util.UUID.class),
                        rs.getString("display_name"), rs.getString("email"), rs.getString("password_hash"),rs.getInt("credentials_version")),
                email.strip().toLowerCase(Locale.ROOT)).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas."));
    }

    @Bean
    RequestLimits requestLimits() { return new RequestLimits(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, RequestLimits limits, JdbcTemplate jdbc,
            @org.springframework.beans.factory.annotation.Value("${app.protection.api-per-minute:1200}") int apiLimit,
            @org.springframework.beans.factory.annotation.Value("${app.protection.login-per-minute:120}") int loginLimit,
            @org.springframework.beans.factory.annotation.Value("${app.protection.account-per-minute:20}") int accountLimit,
            @org.springframework.beans.factory.annotation.Value("${app.protection.booking-per-minute:60}") int bookingLimit,
            @org.springframework.beans.factory.annotation.Value("${app.protection.write-per-minute:120}") int writeLimit,
            @org.springframework.beans.factory.annotation.Value("${app.protection.registration-per-minute:5}") int registrationLimit,
            @org.springframework.beans.factory.annotation.Value("${app.protection.access-per-minute:20}") int accessLimit) throws Exception {
        http.addFilterBefore(new RequestGuardFilter(limits, apiLimit, loginLimit, accountLimit, bookingLimit, writeLimit, registrationLimit, accessLimit),
                org.springframework.security.web.csrf.CsrfFilter.class);
        http.addFilterAfter(new CredentialVersionFilter(jdbc),org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
        http.csrf(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/customer/**").permitAll()
                .requestMatchers("/api/public/**", "/api/auth/csrf", "/api/auth/login", "/api/auth/register", "/api/auth/email/request", "/api/auth/email/confirm", "/api/auth/password/request", "/api/auth/password/reset", "/api/auth/demo-mailbox", "/api/auth/demo-mailbox/open").permitAll()
                .requestMatchers("/api/admin/**", "/api/auth/me").hasRole("OWNER")
                .requestMatchers("/api/**").denyAll()
                .anyRequest().permitAll())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, auth) -> response.setStatus(204))
                .failureHandler((request, response, exception) ->
                    writeError(response, 401, "E-mail ou senha inválidos.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeError(response, 401, "Entre para acessar o painel."))
                .accessDeniedHandler((request, response, exception) ->
                    writeError(response, 403, "A sessão expirou ou a operação não foi autorizada. Atualize a página.")))
            .headers(headers -> headers.referrerPolicy(policy -> policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)).contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
