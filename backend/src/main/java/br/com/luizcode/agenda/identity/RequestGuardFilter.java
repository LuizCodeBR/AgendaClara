package br.com.luizcode.agenda.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs inside the security chain, before CSRF/session allocation and password hashing. */
public final class RequestGuardFilter extends OncePerRequestFilter {
    public static final int MAX_BODY = 16 * 1024;
    private final RequestLimits limits;
    private final int apiLimit, loginLimit, accountLimit, bookingLimit, writeLimit;
    public RequestGuardFilter(RequestLimits limits, int apiLimit, int loginLimit, int accountLimit, int bookingLimit, int writeLimit) {
        if (Math.min(Math.min(apiLimit, loginLimit), Math.min(accountLimit, Math.min(bookingLimit, writeLimit))) < 1)
            throw new IllegalArgumentException("Request limits must be positive");
        this.limits=limits; this.apiLimit=apiLimit; this.loginLimit=loginLimit;
        this.accountLimit=accountLimit; this.bookingLimit=bookingLimit; this.writeLimit=writeLimit;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        // MockMvc has no servlet mapping; real requests use the canonical servlet path.
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/")) { chain.doFilter(request, response); return; }
        response.setHeader("Cache-Control", "no-store");
        if (!admit(response, "api", apiLimit)) return;
        if (request.getContentLengthLong() > MAX_BODY) { reject(response, 413, "A solicitação excede o tamanho permitido."); return; }
        boolean mutation = !java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
        if (mutation && path.startsWith("/api/admin/") && !admit(response, "admin-write", writeLimit)) return;
        if ("POST".equals(request.getMethod()) && path.startsWith("/api/public/") && path.endsWith("/bookings")
                && !admit(response, "booking", bookingLimit)) return;
        if ("POST".equals(request.getMethod()) && path.equals("/api/auth/login")) {
            if (!admit(response, "login", loginLimit)) return;
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            if (username == null || username.length() > 180 || password == null
                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                reject(response, 401, "E-mail ou senha inválidos."); return;
            }
            // Independent of cookies and forwarded IP headers; never keep raw identifiers or passwords.
            if (!admit(response, "account:" + digest(username.strip().toLowerCase(Locale.ROOT)), accountLimit)) return;
        }
        String contentType = request.getContentType();
        if (contentType != null && (contentType.toLowerCase(Locale.ROOT).contains("/json")
                || contentType.toLowerCase(Locale.ROOT).contains("+json"))) {
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) { reject(response, 413, "A solicitação excede o tamanho permitido."); return; }
            request = new BufferedJsonRequest(request, bytes);
        }
        chain.doFilter(request, response);
    }
    private boolean admit(HttpServletResponse response, String key, int limit) throws IOException {
        int retry = limits.acquire(key, limit);
        if (retry == 0) return true;
        response.setHeader("Retry-After", Integer.toString(retry));
        reject(response, 429, "Muitas solicitações em sequência. Aguarde " + retry + " segundos e tente novamente.");
        return false;
    }
    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + message + "\"}");
    }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static final class BufferedJsonRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;
        BufferedJsonRequest(HttpServletRequest request, byte[] bytes) { super(request); this.bytes=bytes; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] b, int offset, int length) { return input.read(b, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous API"); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
