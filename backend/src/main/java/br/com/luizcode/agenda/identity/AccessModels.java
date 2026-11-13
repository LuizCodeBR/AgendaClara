package br.com.luizcode.agenda.identity;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class AccessModels {
    private AccessModels() {}
    public record EmailRequest(@NotBlank @Email @Size(max=180) String email) {
        public EmailRequest { email=email==null?null:email.strip().toLowerCase(Locale.ROOT); }
        @Override public String toString() { return "EmailRequest[redacted]"; }
    }
    public record TokenRequest(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {
        @Override public String toString() { return "TokenRequest[redacted]"; }
    }
    public record ResetRequest(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
                               @NotNull @Size(max=144) String password) {
        @Override public String toString() { return "ResetRequest[redacted]"; }
    }
    public record Notice(String message) {}
    public record Preview(UUID id, String purpose, String to, String subject, String text, String actionUrl, Instant expiresAt) {
        @Override public String toString() { return "Preview[id="+id+"]"; }
    }
    public record OutgoingMail(UUID id, String mailboxDigest, String purpose, String to, String subject,
                               String text, String actionUrl, Instant expiresAt, UUID userId) {
        public Preview preview() { return new Preview(id,purpose,to,subject,text,actionUrl,expiresAt); }
        @Override public String toString() { return "OutgoingMail[id="+id+"]"; }
    }
}
