package br.com.luizcode.agenda.team;

import static br.com.luizcode.agenda.team.TeamModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TeamRepository {
    private final JdbcTemplate jdbc;
    private record Profile(UUID id, String name, String specialty, boolean active, int version) {}
    private static final RowMapper<Profile> PROFILE = (rs, row) -> new Profile(rs.getObject("id", UUID.class),
            rs.getString("name"), rs.getString("specialty"), rs.getBoolean("active"), rs.getInt("version"));

    public TeamRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ProfessionalConfiguration> list(UUID businessId) {
        return jdbc.query("select * from professional where business_id = ? order by active desc, name, id",
                PROFILE, businessId).stream().map(profile -> configuration(businessId, profile)).toList();
    }

    public ProfessionalConfiguration find(UUID businessId, UUID id) {
        var profile = jdbc.query("select * from professional where business_id = ? and id = ?",
                PROFILE, businessId, id).stream().findFirst().orElseThrow(BusinessException::notFound);
        return configuration(businessId, profile);
    }

    private ProfessionalConfiguration configuration(UUID businessId, Profile profile) {
        var hours = jdbc.query("""
                select weekday, opens_at, closes_at from weekly_hours
                where business_id = ? and professional_id = ? order by weekday, opens_at
                """, (rs, row) -> new WeeklyWindow(rs.getInt(1), rs.getObject(2, LocalTime.class),
                        rs.getObject(3, LocalTime.class)), businessId, profile.id());
        return new ProfessionalConfiguration(profile.id(), profile.name(), profile.specialty(), profile.active(),
                profile.version(), hours);
    }

    public ProfessionalConfiguration create(UUID businessId, CreateProfessional request) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into professional(id, business_id, name, specialty, active, version) values (?, ?, ?, ?, TRUE, 0)",
                id, businessId, request.name().strip(), request.specialty().strip());
        return find(businessId, id);
    }

    public void replace(UUID businessId, UUID id, UpdateProfessional request, List<WeeklyWindow> hours) {
        int changed = jdbc.update("""
                update professional set name = ?, specialty = ?, active = ?, version = version + 1
                where business_id = ? and id = ? and version = ?
                """, request.name().strip(), request.specialty().strip(), request.active(), businessId, id,
                request.version());
        if (changed != 1) throw BusinessException.conflict("Os dados foram alterados em outra sessão. Recarregue antes de salvar.");
        jdbc.update("delete from weekly_hours where business_id = ? and professional_id = ?", businessId, id);
        for (var window : hours) jdbc.update("""
                insert into weekly_hours(id, business_id, professional_id, weekday, opens_at, closes_at)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), businessId, id, window.weekday(), window.opensAt(), window.closesAt());
    }

    public record Commitment(Instant startsAt, Instant endsAt, String reference) {}
    public List<Commitment> upcoming(UUID businessId, UUID professionalId, Instant now) {
        return jdbc.query("""
                select starts_at, ends_at, reference from appointment
                where business_id = ? and professional_id = ? and status = 'BOOKED' and ends_at > ?
                order by starts_at
                """, (rs, row) -> new Commitment(rs.getObject(1, OffsetDateTime.class).toInstant(),
                        rs.getObject(2, OffsetDateTime.class).toInstant(), rs.getString(3)),
                businessId, professionalId, now.atOffset(ZoneOffset.UTC));
    }
}
