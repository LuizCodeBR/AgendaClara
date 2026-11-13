package br.com.luizcode.agenda.catalog;

import static br.com.luizcode.agenda.catalog.CatalogModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    private final br.com.luizcode.agenda.identity.MailSettings mail;
    private static final String PUBLIC_POLICY = """
            b.public_booking_enabled=true and not exists (
              select 1 from app_user u where u.business_id=b.id and u.email_verification_required=true
              and u.email_verified_at is null and not (?=true and u.demo_email_verified_at is not null))
            """;
    private static final RowMapper<Business> BUSINESS = (rs, row) ->
            new Business(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                    rs.getString("category"), rs.getString("zone_id"), rs.getString("address"));
    private static final RowMapper<Professional> PROFESSIONAL = (rs, row) ->
            new Professional(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("specialty"));
    private static final RowMapper<Service> SERVICE = (rs, row) ->
            new Service(rs.getObject("id", UUID.class), rs.getString("name"),
                    rs.getString("description"), rs.getInt("duration_minutes"), rs.getInt("price_cents"));

    public CatalogRepository(JdbcTemplate jdbc,br.com.luizcode.agenda.identity.MailSettings mail) { this.jdbc = jdbc; this.mail=mail; }

    public List<Business> businesses() {
        return jdbc.query("select b.* from business b where "+PUBLIC_POLICY+" order by b.name", BUSINESS,mail.demo());
    }

    public Business business(String slug) {
        return jdbc.query("select b.* from business b where b.slug=? and "+PUBLIC_POLICY, BUSINESS, slug,mail.demo())
                .stream().findFirst().orElseThrow(BusinessException::notFound);
    }

    public Business business(UUID id) {
        return jdbc.query("select * from business where id = ?", BUSINESS, id)
                .stream().findFirst().orElseThrow(BusinessException::notFound);
    }

    public List<Professional> professionals(UUID businessId) {
        return jdbc.query("select * from professional where business_id = ? and active = TRUE order by name",
                PROFESSIONAL, businessId);
    }

    public Professional professional(UUID businessId, UUID id) {
        return jdbc.query("select * from professional where business_id = ? and id = ? and active = TRUE",
                PROFESSIONAL, businessId, id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }

    public void lockProfessional(UUID businessId, UUID id) {
        List<UUID> ids = jdbc.query("select id from professional where business_id = ? and id = ? for update",
                (rs, row) -> rs.getObject(1, UUID.class), businessId, id);
        if (ids.isEmpty()) throw BusinessException.notFound();
    }

    public List<Service> services(UUID businessId) {
        return jdbc.query("select * from service_offering where business_id = ? order by duration_minutes, name",
                SERVICE, businessId);
    }

    public Service service(UUID businessId, UUID id) {
        return jdbc.query("select * from service_offering where business_id = ? and id = ?",
                SERVICE, businessId, id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }

    public Service createService(UUID businessId, CreateService request) {
        if (request.durationMinutes() % 15 != 0)
            throw BusinessException.invalid("A duração deve ser um múltiplo de 15 minutos.");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into service_offering (id, business_id, name, description, duration_minutes, price_cents) values (?, ?, ?, ?, ?, ?)",
                id, businessId, request.name().strip(), request.description().strip(), request.durationMinutes(),
                request.priceCents());
        return service(businessId, id);
    }

    public List<WorkingWindow> hours(UUID businessId, UUID professionalId, int weekday) {
        return jdbc.query("select opens_at, closes_at from weekly_hours where business_id = ? and professional_id = ? and weekday = ? order by opens_at",
                (rs, row) -> new WorkingWindow(rs.getObject(1, LocalTime.class), rs.getObject(2, LocalTime.class)),
                businessId, professionalId, weekday);
    }
}
