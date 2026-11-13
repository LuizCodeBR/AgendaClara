package br.com.luizcode.agenda.catalog;

import br.com.luizcode.agenda.catalog.CatalogModels.Business;
import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessSetupService {
    public record PublicationRequest(@NotNull Boolean published, @NotNull @PositiveOrZero Integer version) {}
    public record Setup(Business business, boolean published, int version, int services, int professionals,
                        boolean hasHours, boolean ready) {}
    private final JdbcTemplate jdbc;
    private final CatalogRepository catalog;
    private final Clock clock;
    private final int horizon;
    public BusinessSetupService(JdbcTemplate jdbc, CatalogRepository catalog, Clock clock,
            @Value("${app.booking.horizon-days:60}") int horizon) {
        this.jdbc=jdbc; this.catalog=catalog; this.clock=clock; this.horizon=horizon;
    }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Setup get(UUID businessId) { return snapshot(businessId); }

    @Transactional
    public Setup publish(UUID businessId, PublicationRequest request) {
        var versions=jdbc.query("select publication_version from business where id=? for update",
                (rs,row)->rs.getInt(1),businessId);
        if (versions.isEmpty()) throw BusinessException.notFound();
        if (versions.getFirst()!=request.version().intValue())
            throw BusinessException.conflict("A publicação foi alterada em outra janela. Atualize o painel.");
        Setup state=snapshot(businessId);
        if (request.published() && !state.ready())
            throw BusinessException.invalid("Cadastre pelo menos um serviço, um profissional ativo e horários de atendimento antes de publicar.");
        jdbc.update("update business set public_booking_enabled=?,publication_version=publication_version+1 where id=?",
                request.published(),businessId);
        return snapshot(businessId);
    }

    private Setup snapshot(UUID businessId) {
        Business business=catalog.business(businessId);
        int services=jdbc.queryForObject("select count(*) from service_offering where business_id=?",Integer.class,businessId);
        int professionals=jdbc.queryForObject("select count(*) from professional where business_id=? and active=true",Integer.class,businessId);
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(business.zoneId())));
        boolean hasHours=jdbc.queryForObject("""
                select count(*) from professional p where p.business_id=? and p.active=true and (
                  exists(select 1 from weekly_hours h where h.business_id=p.business_id and h.professional_id=p.id)
                  or exists(select 1 from date_hours d join date_hours_window w on w.business_id=d.business_id and w.exception_id=d.id
                    where d.business_id=p.business_id and d.professional_id=p.id and d.exception_date between ? and ?))
                """,Integer.class,businessId,today,today.plusDays(horizon))>0;
        return jdbc.query("select public_booking_enabled,publication_version from business where id=?",
                (rs,row)->new Setup(business,rs.getBoolean(1),rs.getInt(2),services,professionals,hasHours,
                        services>0 && professionals>0 && hasHours),businessId).getFirst();
    }
}
