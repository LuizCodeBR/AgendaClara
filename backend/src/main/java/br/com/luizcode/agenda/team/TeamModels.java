package br.com.luizcode.agenda.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class TeamModels {
    private TeamModels() {}

    public record WeeklyWindow(@Min(1) @Max(7) int weekday, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
    public record ProfessionalConfiguration(UUID id, String name, String specialty, boolean active,
                                            int version, List<WeeklyWindow> hours) {}
    public record CreateProfessional(@NotBlank @Size(max = 100) String name,
                                     @NotBlank @Size(max = 160) String specialty) {}
    public record UpdateProfessional(@NotBlank @Size(max = 100) String name,
                                     @NotBlank @Size(max = 160) String specialty,
                                     @NotNull Boolean active, @NotNull @Min(0) Integer version,
                                     @NotNull @Size(max = 28) List<@NotNull @Valid WeeklyWindow> hours) {}
}
