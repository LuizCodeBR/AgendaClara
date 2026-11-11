package br.com.luizcode.agenda.scheduling;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ScheduleModels {
    private ScheduleModels() {}
    public record Slot(Instant startsAt, Instant endsAt) {}
    public record Availability(LocalDate date, String zoneId, List<Slot> slots) {}
    public record BookingRequest(@NotNull UUID requestKey, @NotNull UUID professionalId,
            @NotNull UUID serviceId, @NotNull OffsetDateTime startsAt,
            @NotBlank @Size(max = 100) String customerName,
            @NotBlank @Pattern(regexp = "^[+0-9() .-]{8,25}$", message = "informe um telefone válido") String customerPhone) {}
    public record BookingReceipt(String reference, String serviceName, String professionalName,
                                 Instant startsAt, Instant endsAt, int priceCents) {}
    public record BookingOutcome(BookingReceipt receipt, boolean created) {}
    public record AppointmentView(UUID id, UUID professionalId, UUID serviceId, String professionalName,
                                  String serviceName, String customerName, String customerPhone,
                                  Instant startsAt, Instant endsAt, String status, String reference, int priceCents) {}
    public record RescheduleRequest(@NotNull OffsetDateTime startsAt) {}
    public record BlockRequest(@NotNull UUID professionalId, @NotNull OffsetDateTime startsAt,
                               @NotNull OffsetDateTime endsAt, @NotBlank @Size(max = 160) String reason) {}
    public record BlockView(UUID id, UUID professionalId, Instant startsAt, Instant endsAt, String reason) {}
    public record DayAgenda(LocalDate date, String zoneId, List<AppointmentView> appointments, List<BlockView> blocks) {}
}
