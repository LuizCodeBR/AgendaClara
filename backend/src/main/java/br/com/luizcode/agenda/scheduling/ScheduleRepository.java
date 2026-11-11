package br.com.luizcode.agenda.scheduling;

import static br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleRepository {
    private final JdbcTemplate jdbc;
    private static final String APPOINTMENTS = """
            select a.*, p.name as professional_name from appointment a
            join professional p on p.id = a.professional_id and p.business_id = a.business_id
            """;
    public record Appointment(UUID id, UUID businessId, UUID professionalId, UUID serviceId, String professionalName,
                              String serviceName, String customerName, String customerPhone, Instant startsAt,
                              Instant endsAt, String status, String reference, int priceCents, String fingerprint) {
        public AppointmentView view() {
            return new AppointmentView(id, professionalId, serviceId, professionalName, serviceName, customerName,
                    customerPhone, startsAt, endsAt, status, reference, priceCents);
        }
        public BookingReceipt receipt() {
            return new BookingReceipt(reference, serviceName, professionalName, startsAt, endsAt, priceCents);
        }
    }
    private static final RowMapper<Appointment> MAPPER = (rs, row) -> new Appointment(
            rs.getObject("id", UUID.class), rs.getObject("business_id", UUID.class),
            rs.getObject("professional_id", UUID.class), rs.getObject("service_id", UUID.class),
            rs.getString("professional_name"), rs.getString("service_name"), rs.getString("customer_name"),
            rs.getString("customer_phone"), rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
            rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getString("status"),
            rs.getString("reference"), rs.getInt("price_cents"), rs.getString("request_fingerprint"));
    public ScheduleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Appointment appointment(UUID businessId, UUID id) {
        return jdbc.query(APPOINTMENTS + " where a.business_id = ? and a.id = ?", MAPPER, businessId, id)
                .stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    public Optional<Appointment> byRequestKey(UUID businessId, UUID key) {
        return jdbc.query(APPOINTMENTS + " where a.business_id = ? and a.request_key = ?",
                MAPPER, businessId, key).stream().findFirst();
    }
    public List<Appointment> day(UUID businessId, Instant start, Instant end) {
        return jdbc.query(APPOINTMENTS + " where a.business_id = ? and a.starts_at < ? and a.ends_at > ? order by a.starts_at, p.name",
                MAPPER, businessId, utc(end), utc(start));
    }
    public List<Slot> occupied(UUID businessId, UUID professionalId, Instant start, Instant end) {
        return jdbc.query("""
                select starts_at, ends_at from appointment
                where business_id = ? and professional_id = ? and status = 'BOOKED'
                  and starts_at < ? and ends_at > ?
                union all
                select starts_at, ends_at from time_block
                where business_id = ? and professional_id = ? and starts_at < ? and ends_at > ?
                """, (rs, row) -> new Slot(rs.getObject(1, OffsetDateTime.class).toInstant(),
                        rs.getObject(2, OffsetDateTime.class).toInstant()),
                businessId, professionalId, utc(end), utc(start), businessId, professionalId, utc(end), utc(start));
    }
    public boolean overlaps(UUID businessId, UUID professionalId, Instant start, Instant end, UUID excludedId) {
        Integer appointments = jdbc.queryForObject("""
                select count(*) from appointment
                where business_id = ? and professional_id = ? and status = 'BOOKED'
                  and starts_at < ? and ends_at > ? and id <> ?
                """, Integer.class, businessId, professionalId, utc(end), utc(start), excludedId);
        Integer blocks = jdbc.queryForObject("""
                select count(*) from time_block where business_id = ? and professional_id = ?
                  and starts_at < ? and ends_at > ?
                """, Integer.class, businessId, professionalId, utc(end), utc(start));
        return appointments != null && appointments > 0 || blocks != null && blocks > 0;
    }
    public Appointment insert(UUID businessId, BookingRequest request, String serviceName, int priceCents,
                              Instant start, Instant end, String phone, String fingerprint, Instant now) {
        UUID id = UUID.randomUUID();
        String reference = id.toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        jdbc.update("""
                insert into appointment
                (id, business_id, professional_id, service_id, service_name, price_cents, customer_name, customer_phone,
                 starts_at, ends_at, status, reference, request_key, request_fingerprint, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BOOKED', ?, ?, ?, ?, ?)
                """, id, businessId, request.professionalId(), request.serviceId(), serviceName, priceCents,
                request.customerName().strip(), phone, utc(start), utc(end), reference, request.requestKey(),
                fingerprint, utc(now), utc(now));
        return appointment(businessId, id);
    }
    public void cancel(UUID businessId, UUID id, Instant now) {
        jdbc.update("update appointment set status = 'CANCELLED', updated_at = ? where business_id = ? and id = ?",
                utc(now), businessId, id);
    }
    public void move(UUID businessId, UUID id, Instant start, Instant end, Instant now) {
        jdbc.update("update appointment set starts_at = ?, ends_at = ?, updated_at = ? where business_id = ? and id = ?",
                utc(start), utc(end), utc(now), businessId, id);
    }
    public BlockView insertBlock(UUID businessId, BlockRequest request) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into time_block (id, business_id, professional_id, starts_at, ends_at, reason) values (?, ?, ?, ?, ?, ?)",
                id, businessId, request.professionalId(), request.startsAt(), request.endsAt(), request.reason().strip());
        return new BlockView(id, request.professionalId(), request.startsAt().toInstant(),
                request.endsAt().toInstant(), request.reason().strip());
    }
    private static final RowMapper<BlockView> BLOCK_MAPPER = (rs, row) -> new BlockView(
            rs.getObject("id", UUID.class), rs.getObject("professional_id", UUID.class),
            rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
            rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getString("reason"));
    public List<BlockView> blocks(UUID businessId, Instant start, Instant end) {
        return jdbc.query("select * from time_block where business_id = ? and starts_at < ? and ends_at > ? order by starts_at",
                BLOCK_MAPPER, businessId, utc(end), utc(start));
    }
    public BlockView block(UUID businessId, UUID id) {
        return jdbc.query("select * from time_block where business_id = ? and id = ?",
                BLOCK_MAPPER, businessId, id).stream().findFirst().orElseThrow(BusinessException::notFound);
    }
    public void deleteBlock(UUID businessId, UUID id) {
        jdbc.update("delete from time_block where business_id = ? and id = ?", businessId, id);
    }
    private static OffsetDateTime utc(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
