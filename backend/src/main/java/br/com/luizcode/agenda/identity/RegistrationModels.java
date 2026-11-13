package br.com.luizcode.agenda.identity;

import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.UUID;

public final class RegistrationModels {
    private RegistrationModels() {}
    public record RegisterRequest(
            @NotNull UUID requestKey,
            @NotBlank @Size(min=2, max=120) String businessName,
            @NotBlank @Size(max=80) String category,
            @NotBlank @Size(max=200) String address,
            @NotBlank @Size(max=80) String zoneId,
            @NotBlank @Size(min=2, max=100) String ownerName,
            @NotBlank @Email @Size(max=180) String email,
            @NotNull @Size(max=144) String password,
            boolean demoAcknowledged) {
        public RegisterRequest {
            businessName = clean(businessName); category = clean(category);
            address = clean(address); zoneId = clean(zoneId); ownerName = clean(ownerName);
            email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        }
        @Override public String toString() { return "RegisterRequest[requestKey=" + requestKey + "]"; }
    }
    private static String clean(String text) { return text == null ? null : text.strip(); }
    public record RegistrationReceipt(UUID businessId, String businessName, String slug, String email) {}
    public record RegistrationOutcome(RegistrationReceipt receipt, boolean created) {}
}
