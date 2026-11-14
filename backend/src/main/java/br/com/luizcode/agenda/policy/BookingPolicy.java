package br.com.luizcode.agenda.policy;

import java.time.*;
import br.com.luizcode.agenda.shared.BusinessException;

/** Minutes are elapsed time; deadlines are instants, independent of display timezone. */
public record BookingPolicy(int bookingNoticeMinutes,int cancellationNoticeMinutes,int rescheduleNoticeMinutes,int version) {
    public static final BookingPolicy NONE=new BookingPolicy(0,0,0,0);
    public boolean allowsBooking(Instant start,Instant now){return allows(start,now,bookingNoticeMinutes);}
    public boolean allowsCancellation(Instant start,Instant now){return allows(start,now,cancellationNoticeMinutes);}
    public boolean allowsReschedule(Instant start,Instant now){return allows(start,now,rescheduleNoticeMinutes);}
    public Instant cancelUntil(Instant start){return start.minus(Duration.ofMinutes(cancellationNoticeMinutes));}
    public Instant rescheduleUntil(Instant start){return start.minus(Duration.ofMinutes(rescheduleNoticeMinutes));}
    private static boolean allows(Instant start,Instant now,int minutes){
        return start.isAfter(now)&&!now.isAfter(start.minus(Duration.ofMinutes(minutes)));
    }
    public void requireBooking(Instant start,Instant now){
        if(!allowsBooking(start,now))throw BusinessException.conflict("Este horário não atende à antecedência mínima de "+bookingNoticeMinutes+" minutos. Atualize os horários disponíveis.");
    }
}
