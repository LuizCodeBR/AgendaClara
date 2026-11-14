package br.com.luizcode.agenda.policy;

import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class BookingPolicyService {
    public record Save(@NotNull @Min(0) @Max(10080) Integer bookingNoticeMinutes,
        @NotNull @Min(0) @Max(10080) Integer cancellationNoticeMinutes,
        @NotNull @Min(0) @Max(10080) Integer rescheduleNoticeMinutes,@NotNull @Min(0) Integer version) {}
    private final JdbcTemplate jdbc;
    public BookingPolicyService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public BookingPolicy get(UUID business){
        return jdbc.query("select booking_notice_minutes,cancel_notice_minutes,reschedule_notice_minutes,booking_policy_version from business where id=?",
            (rs,row)->new BookingPolicy(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4)),business).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    /** Caller holds a write transaction; use a non-key UPDATE so PostgreSQL FK checks remain compatible. */
    public BookingPolicy lock(UUID business){
        if(jdbc.update("update business set booking_policy_version=booking_policy_version where id=?",business)!=1)throw BusinessException.notFound();
        return get(business);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BookingPolicy save(UUID business,Save request){
        var current=lock(business);
        if(current.version()!=request.version())throw BusinessException.conflict("As regras foram alteradas em outra janela. Atualize os dados antes de salvar.");
        if(current.bookingNoticeMinutes()==request.bookingNoticeMinutes()&&current.cancellationNoticeMinutes()==request.cancellationNoticeMinutes()
            &&current.rescheduleNoticeMinutes()==request.rescheduleNoticeMinutes())return current;
        jdbc.update("update business set booking_notice_minutes=?,cancel_notice_minutes=?,reschedule_notice_minutes=?,booking_policy_version=booking_policy_version+1 where id=?",
            request.bookingNoticeMinutes(),request.cancellationNoticeMinutes(),request.rescheduleNoticeMinutes(),business);
        return get(business);
    }
}
