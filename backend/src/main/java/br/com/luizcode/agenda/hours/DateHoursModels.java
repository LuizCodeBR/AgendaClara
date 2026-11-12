package br.com.luizcode.agenda.hours;

import jakarta.validation.Valid;
import br.com.luizcode.agenda.team.TeamModels.WeeklyWindow;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class DateHoursModels {
    private DateHoursModels() {}
    public record DayWindow(@NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
    public record DateException(LocalDate date, String reason, List<DayWindow> hours) {}
    public record ExceptionCalendar(int version, LocalDate firstDate, LocalDate lastDate, List<DateException> exceptions, List<WeeklyWindow> weeklyHours) {}
    public record SaveException(@NotNull @Min(0) Integer version, @NotBlank @Size(max=160) String reason,
                                @NotNull @Size(max=4) List<@NotNull @Valid DayWindow> hours) {}
}
