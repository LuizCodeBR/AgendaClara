package br.com.luizcode.agenda.catalog;

import java.time.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import br.com.luizcode.agenda.shared.BusinessException;

public final class WorkingHoursPolicy {
    private WorkingHoursPolicy() {}

    public static List<WorkingWindow> normalize(List<WorkingWindow> input) {
        if(input.size()>4) throw BusinessException.invalid("Use até quatro intervalos por dia.");
        for(var window:input) {
            if(window==null || !grid(window.opensAt()) || !grid(window.closesAt())
                    || !window.opensAt().isBefore(window.closesAt()))
                throw BusinessException.invalid("Cada intervalo deve começar antes de terminar, no mesmo dia e em múltiplos de 15 minutos.");
        }
        var sorted=input.stream().sorted(Comparator.comparing(WorkingWindow::opensAt)).toList();
        List<WorkingWindow> result=new ArrayList<>();
        for(var window:sorted) {
            if(!result.isEmpty()) {
                var previous=result.getLast();
                if(window.opensAt().isBefore(previous.closesAt()))
                    throw BusinessException.invalid("Os intervalos de um mesmo dia não podem se sobrepor.");
                if(window.opensAt().equals(previous.closesAt())) {
                    result.set(result.size()-1,new WorkingWindow(previous.opensAt(),window.closesAt()));
                    continue;
                }
            }
            result.add(window);
        }
        return List.copyOf(result);
    }
    private static boolean grid(LocalTime value) {
        return value!=null && value.getSecond()==0 && value.getNano()==0 && value.getMinute()%15==0;
    }

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
