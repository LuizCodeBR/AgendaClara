package br.com.luizcode.agenda.catalog;

import java.time.*;
import java.util.List;

public final class WorkingHoursPolicy {
    private WorkingHoursPolicy() {}

    public static boolean contains(Instant start, Instant end, ZoneId zone, List<WorkingWindow> windows) {
        ZonedDateTime local = start.atZone(zone);
        ZonedDateTime localEnd = end.atZone(zone);
        if (!end.isAfter(start) || !local.toLocalDate().equals(localEnd.toLocalDate())
                || local.getSecond() != 0 || local.getNano() != 0
                || !local.getOffset().equals(localEnd.getOffset())
                || zone.getRules().getValidOffsets(local.toLocalDateTime()).size() != 1
                || zone.getRules().getValidOffsets(localEnd.toLocalDateTime()).size() != 1) return false;
        return windows.stream().anyMatch(window ->
                !local.toLocalTime().isBefore(window.opensAt())
                        && !localEnd.toLocalTime().isAfter(window.closesAt())
                        && Duration.between(window.opensAt(), local.toLocalTime()).toMinutes() % 15 == 0);
    }
}
