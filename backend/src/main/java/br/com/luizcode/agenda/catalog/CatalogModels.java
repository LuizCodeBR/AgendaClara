package br.com.luizcode.agenda.catalog;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CatalogModels {
    private CatalogModels() {}

    public record Business(UUID id, String slug, String name, String category, String zoneId, String address) {}
    public record Professional(UUID id, String name, String specialty, List<UUID> serviceIds) {}
    public record Service(UUID id, String name, String description, int durationMinutes, int priceCents) {}
    public record Catalog(Business business, List<Service> services, List<Professional> professionals,
                          LocalDate suggestedDate, int horizonDays) {}
    public record CreateService(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 240) String description,
            @Min(15) @Max(240) int durationMinutes,
            @Min(0) @Max(1000000) int priceCents) {}
}
