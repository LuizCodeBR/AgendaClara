package br.com.luizcode.agenda.scheduling;

import static br.com.luizcode.agenda.catalog.CatalogModels.*;
import static br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.catalog.EffectiveHours;
import br.com.luizcode.agenda.catalog.WorkingWindow;
import br.com.luizcode.agenda.catalog.WorkingHoursPolicy;
import br.com.luizcode.agenda.shared.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {
    private static final UUID NO_APPOINTMENT = new UUID(0, 0);
    private final CatalogRepository catalog;
    private final EffectiveHours hours;
    private final ScheduleRepository schedule;
    private final Clock clock;
    private final br.com.luizcode.agenda.customer.CustomerAccessRepository customerAccess;
    private final br.com.luizcode.agenda.customer.CustomerKeys customerKeys;
    private final int horizonDays;
    private final br.com.luizcode.agenda.policy.BookingPolicyService policies;

    public ScheduleService(CatalogRepository catalog, ScheduleRepository schedule, Clock clock, EffectiveHours hours, br.com.luizcode.agenda.customer.CustomerAccessRepository customerAccess,
                           br.com.luizcode.agenda.customer.CustomerKeys customerKeys,
                           br.com.luizcode.agenda.policy.BookingPolicyService policies,@Value("${app.booking.horizon-days:60}") int horizonDays) {
        this.policies=policies;
        this.customerAccess=customerAccess;this.customerKeys=customerKeys;
        this.catalog = catalog;
        this.hours = hours;
        this.schedule = schedule;
        this.clock = clock;
        this.horizonDays = horizonDays;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Catalog catalog(Business business) {
        var policy=policies.get(business.id());
        ZoneId zone=ZoneId.of(business.zoneId());
        LocalDate suggested = LocalDate.now(clock.withZone(zone)).plusDays(1);
        LocalDate earliest=clock.instant().plus(Duration.ofMinutes(policy.bookingNoticeMinutes())).atZone(zone).toLocalDate();
        if(earliest.isAfter(suggested))suggested=earliest;
        if (suggested.getDayOfWeek() == DayOfWeek.SUNDAY) suggested = suggested.plusDays(1);
        return new Catalog(business, catalog.services(business.id()), catalog.professionals(business.id()),
                suggested, horizonDays,policy);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Availability availability(Business business, UUID professionalId, UUID serviceId, LocalDate date) {
        var service = catalog.service(business.id(), serviceId);
        return availability(business,professionalId,serviceId,date,service.durationMinutes(),NO_APPOINTMENT,policies.get(business.id()).bookingNoticeMinutes());
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Availability customerAvailability(Business business, ScheduleRepository.Appointment appointment,LocalDate date) {
        int minutes=Math.toIntExact(Duration.between(appointment.startsAt(),appointment.endsAt()).toMinutes());
        return availability(business,appointment.professionalId(),appointment.serviceId(),date,minutes,appointment.id(),appointment.bookingPolicy().bookingNoticeMinutes());
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Availability adminAvailability(Business business,UUID id,LocalDate date){
        var appointment=schedule.appointment(business.id(),id);
        if(!appointment.status().equals("BOOKED")||!appointment.startsAt().isAfter(clock.instant()))
            throw BusinessException.conflict("Só é possível remarcar atendimentos futuros confirmados.");
        int minutes=Math.toIntExact(Duration.between(appointment.startsAt(),appointment.endsAt()).toMinutes());
        return availability(business,appointment.professionalId(),appointment.serviceId(),date,minutes,id,0);
    }

    private Availability availability(Business business,UUID professionalId,UUID serviceId,LocalDate date,int durationMinutes,UUID excluded,int noticeMinutes) {
        catalog.professional(business.id(), professionalId);
        catalog.requireServiceAssignment(business.id(), professionalId, serviceId);
        ZoneId zone = ZoneId.of(business.zoneId());
        validateDate(date, zone);
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Slot> occupied = schedule.occupied(business.id(), professionalId, dayStart, dayEnd,excluded);
        List<Slot> slots = new ArrayList<>();
        var windows = hours.onDate(business.id(), professionalId, date);
        for (var window : windows) {
            LocalDateTime close = date.atTime(window.closesAt());
            for (LocalDateTime cursor = date.atTime(window.opensAt());
                 !cursor.plusMinutes(durationMinutes).isAfter(close); cursor = cursor.plusMinutes(15)) {
                // Do not silently reinterpret ambiguous/nonexistent times during daylight-saving transitions.
                if (zone.getRules().getValidOffsets(cursor).size() != 1) continue;
                Instant start = cursor.atZone(zone).toInstant();
                Instant end = start.plusSeconds(durationMinutes * 60L);
                if (validWindow(start, end, zone, windows) && start.isAfter(clock.instant())
                        && !start.isBefore(clock.instant().plus(Duration.ofMinutes(noticeMinutes)))
                        && occupied.stream().noneMatch(item -> intersects(start, end, item.startsAt(), item.endsAt()))) {
                    slots.add(new Slot(start, end));
                }
            }
        }
        return new Availability(date, business.zoneId(), List.copyOf(slots));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingOutcome book(Business business, BookingRequest request) { return bookInternal(business,request,false); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public BookingOutcome bookPublic(Business business,BookingRequest request){return bookInternal(business,request,true);}

    private BookingOutcome bookInternal(Business business,BookingRequest request,boolean requirePublic) {
        // Policy is locked before the professional so a booking cannot accept stale rules.
        var policy=policies.lock(business.id());
        if(requirePublic)catalog.business(business.slug());
        // All schedule writes lock the same database row before checking conflicts.
        // This serializes one professional's writes even across application instances.
        catalog.lockProfessional(business.id(), request.professionalId());
        String phone = normalizePhone(request.customerPhone());
        String fingerprint = fingerprint(request, phone);
        var existing = schedule.byRequestKey(business.id(), request.requestKey());
        if (existing.isPresent()) {
            if (!existing.get().fingerprint().equals(fingerprint))
                throw BusinessException.conflict("Esta tentativa já foi usada com outros dados.");
            if (existing.get().status().equals("CANCELLED"))
                throw BusinessException.conflict("Essa reserva foi cancelada. Faça uma nova solicitação.");
            return new BookingOutcome(customerReceipt(existing.get()), false);
        }
        if((request.policyVersion()==null&&policy.version()!=0)||(request.policyVersion()!=null&&request.policyVersion()!=policy.version()))
            throw BusinessException.conflict("As regras do estabelecimento mudaram. Atualize os dados e confira os prazos antes de confirmar.");
        catalog.professional(business.id(), request.professionalId());
        var service = catalog.service(business.id(), request.serviceId());
        catalog.requireServiceAssignment(business.id(), request.professionalId(), request.serviceId());
        Instant start = request.startsAt().toInstant();
        Instant end = start.plusSeconds(service.durationMinutes() * 60L);
        policy.requireBooking(start,clock.instant());
        requireFree(business, request.professionalId(), start, end, NO_APPOINTMENT);
        var saved = schedule.insert(business.id(), request, service.name(), service.priceCents(),
                start, end, phone, fingerprint, clock.instant(),policy);
        if(customerKeys.available())
            customerAccess.save(business.id(),saved.id(),1,br.com.luizcode.agenda.customer.CustomerKeys.digest(customerKeys.token(saved.id(),1)),clock.instant(),null);
        return new BookingOutcome(customerReceipt(saved), true);
    }

    private BookingReceipt customerReceipt(ScheduleRepository.Appointment appointment) {
        var receipt=appointment.receipt();
        var access=customerAccess.access(appointment.businessId(),appointment.id()).orElse(null);
        if(!customerKeys.available()||access==null||access.generation()!=1||access.revokedAt()!=null
                ||!br.com.luizcode.agenda.customer.CustomerKeys.validUntil(appointment.endsAt()).isAfter(clock.instant()))return receipt;
        String token=customerKeys.token(appointment.id(),1);
        if(!br.com.luizcode.agenda.customer.CustomerKeys.matches(token,access.digest()))return receipt;
        return new BookingReceipt(receipt.reference(),receipt.serviceName(),receipt.professionalName(),receipt.startsAt(),receipt.endsAt(),receipt.priceCents(),
                token,br.com.luizcode.agenda.customer.CustomerKeys.validUntil(appointment.endsAt()),appointment.bookingPolicy());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DayAgenda day(Business business, LocalDate date) {
        ZoneId zone = ZoneId.of(business.zoneId());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isBefore(today.minusDays(365)) || date.isAfter(today.plusDays(365)))
            throw BusinessException.invalid("Consulte uma data até um ano antes ou depois de hoje.");
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return new DayAgenda(date, business.zoneId(),
                schedule.day(business.id(), start, end).stream().map(ScheduleRepository.Appointment::view).toList(),
                schedule.blocks(business.id(), start, end));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentView cancel(Business business, UUID id) { return cancel(business,id,null); }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AppointmentView cancel(Business business, UUID id,Integer version) {
        var first = schedule.appointment(business.id(), id);
        catalog.lockProfessional(business.id(), first.professionalId());
        var current = schedule.appointment(business.id(), id);
        if (current.status().equals("CANCELLED")) return current.view();
        requireVersion(current,version);
        if (!current.startsAt().isAfter(clock.instant()))
            throw BusinessException.invalid("Só é possível cancelar atendimentos futuros.");
        schedule.cancel(business.id(), id, clock.instant());
        return schedule.appointment(business.id(), id).view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentView reschedule(Business business, UUID id, RescheduleRequest request) {
        var first = schedule.appointment(business.id(), id);
        catalog.lockProfessional(business.id(), first.professionalId());
        var current = schedule.appointment(business.id(), id);
        catalog.requireServiceAssignment(business.id(), current.professionalId(), current.serviceId());
        if (!current.status().equals("BOOKED"))
            throw BusinessException.conflict("Um agendamento cancelado não pode ser remarcado.");
        if (!current.startsAt().isAfter(clock.instant()))
            throw BusinessException.invalid("Só é possível remarcar atendimentos futuros.");
        if(request.version()!=null && request.version()==current.version()-1 && request.startsAt().toInstant().equals(current.startsAt()))
            return current.view();
        requireVersion(current,request.version());
        Instant start = request.startsAt().toInstant();
        Instant end = start.plus(Duration.between(current.startsAt(), current.endsAt()));
        requireFree(business, current.professionalId(), start, end, id);
        schedule.move(business.id(), id, start, end, clock.instant());
        return schedule.appointment(business.id(), id).view();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BlockView block(Business business, BlockRequest request) {
        catalog.lockProfessional(business.id(), request.professionalId());
        catalog.professional(business.id(), request.professionalId());
        Instant start = request.startsAt().toInstant();
        Instant end = request.endsAt().toInstant();
        ZoneId zone = ZoneId.of(business.zoneId());
        validateDate(start.atZone(zone).toLocalDate(), zone);
        if (!start.isAfter(clock.instant()) || !end.isAfter(start)
                || !start.atZone(zone).toLocalDate().equals(end.atZone(zone).toLocalDate()))
            throw BusinessException.invalid("O bloqueio deve ser futuro, com início e fim no mesmo dia.");
        if (schedule.overlaps(business.id(), request.professionalId(), start, end, NO_APPOINTMENT))
            throw BusinessException.conflict("Há um agendamento ou bloqueio nesse intervalo.");
        return schedule.insertBlock(business.id(), request);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeBlock(Business business, UUID id) {
        var current = schedule.block(business.id(), id);
        catalog.lockProfessional(business.id(), current.professionalId());
        schedule.deleteBlock(business.id(), id);
    }

    private void requireVersion(ScheduleRepository.Appointment appointment,Integer version){
        if(version!=null&&version!=appointment.version())
            throw BusinessException.conflict("A reserva foi alterada em outra janela. Atualize os dados antes de confirmar.");
    }

    private void requireFree(Business business, UUID professionalId, Instant start, Instant end, UUID excludedId) {
        catalog.professional(business.id(), professionalId);
        ZoneId zone = ZoneId.of(business.zoneId());
        validateDate(start.atZone(zone).toLocalDate(), zone);
        if (!start.isAfter(clock.instant()))
            throw BusinessException.invalid("Escolha um horário futuro.");
        var windows = hours.onDate(business.id(), professionalId, start.atZone(zone).toLocalDate());
        if (!validWindow(start, end, zone, windows))
            throw BusinessException.invalid("O horário não cabe no expediente. Use os horários disponíveis.");
        if (schedule.overlaps(business.id(), professionalId, start, end, excludedId))
            throw BusinessException.conflict("Esse horário acabou de ser ocupado. Escolha outro.");
    }

    private boolean validWindow(Instant start, Instant end, ZoneId zone, List<WorkingWindow> windows) {
        return WorkingHoursPolicy.contains(start, end, zone, windows);
    }

    private void validateDate(LocalDate date, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isBefore(today) || date.isAfter(today.plusDays(horizonDays)))
            throw BusinessException.invalid("Escolha uma data entre hoje e os próximos " + horizonDays + " dias.");
    }

    private String normalizePhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 15)
            throw BusinessException.invalid("Informe um telefone com 8 a 15 dígitos.");
        return digits;
    }

    private String fingerprint(BookingRequest request, String phone) {
        List<String> parts = List.of(request.professionalId().toString(), request.serviceId().toString(),
                request.startsAt().toInstant().toString(), request.customerName().strip(), phone);
        String canonical = parts.stream().map(value -> value.length() + ":" + value)
                .collect(java.util.stream.Collectors.joining());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    private static boolean intersects(Instant start, Instant end, Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }
}
