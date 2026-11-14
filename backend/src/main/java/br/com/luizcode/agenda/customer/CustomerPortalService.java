package br.com.luizcode.agenda.customer;

import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.catalog.CatalogModels.Business;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CustomerPortalService {
    public record Exchange(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){
        @Override public String toString(){return "Exchange[protected]";}
    }
    public record Change(@NotNull @Min(0) Integer version){}
    public record Move(@NotNull java.time.OffsetDateTime startsAt,@NotNull @Min(0) Integer version){}
    public record Issue(@NotNull UUID requestKey,@NotNull @Min(0) Integer version){}
    public record BusinessDetails(String name,String slug,String address,String zoneId){}
    public record View(UUID reservationKey,BusinessDetails business,String reference,String serviceName,String professionalName,String customerName,
            Instant startsAt,Instant endsAt,int priceCents,String status,int version,boolean canChange,
            Instant accessValidUntil,Instant sessionExpiresAt,LocalDate suggestedDate,int horizonDays,
            br.com.luizcode.agenda.policy.BookingPolicy bookingPolicy,boolean canCancel,boolean canReschedule,Instant cancelUntil,Instant rescheduleUntil){}
    public record AccessState(boolean available,boolean active,int version,Instant validUntil){}
    public record Issued(String token,int version,Instant validUntil){
        @Override public String toString(){return "Issued[protected]";}
    }
    public record Opened(String secret,Instant expiresAt,View view){
        @Override public String toString(){return "Opened[protected]";}
    }
    private final CustomerAccessRepository access;
    private final CustomerKeys keys;
    private final CatalogRepository catalog;
    private final ScheduleRepository appointments;
    private final ScheduleService schedule;
    private final Clock clock;
    private final int horizon;
    public CustomerPortalService(CustomerAccessRepository access,CustomerKeys keys,CatalogRepository catalog,
            ScheduleRepository appointments,ScheduleService schedule,Clock clock,@Value("${app.booking.horizon-days:60}") int horizon){
        this.access=access;this.keys=keys;this.catalog=catalog;this.appointments=appointments;this.schedule=schedule;this.clock=clock;this.horizon=horizon;
    }
    private static BusinessException denied(){return new BusinessException(HttpStatus.UNAUTHORIZED,
            "O acesso à reserva é inválido ou expirou. Abra seu link privado novamente ou fale com o estabelecimento.");}
    private void validUntil(ScheduleRepository.Appointment appointment){
        if(!CustomerKeys.validUntil(appointment.endsAt()).isAfter(clock.instant()))throw denied();
    }
    private CustomerAccessRepository.Grant grant(String secret,UUID reservation){
        if(!CustomerKeys.valid(secret))throw denied();
        var grant=access.grant(CustomerKeys.digest(secret),clock.instant()).orElseThrow(CustomerPortalService::denied);
        if(!grant.appointmentId().equals(reservation))throw denied();
        validUntil(appointments.appointment(grant.businessId(),grant.appointmentId()));
        return grant;
    }
    private CustomerAccessRepository.Grant lockedGrant(String secret,UUID reservation){
        var before=grant(secret,reservation);
        catalog.lockProfessional(before.businessId(),before.professionalId());
        return grant(secret,reservation);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Opened open(String token,String previous){
        if(!keys.available()||!CustomerKeys.valid(token))throw denied();
        var before=access.byDigest(CustomerKeys.digest(token)).orElseThrow(CustomerPortalService::denied);
        catalog.lockProfessional(before.businessId(),before.professionalId());
        var current=access.byDigest(CustomerKeys.digest(token)).orElseThrow(CustomerPortalService::denied);
        var appointment=appointments.appointment(current.businessId(),current.appointmentId());validUntil(appointment);
        String secret=CustomerKeys.random();Instant expires=clock.instant().plus(Duration.ofHours(1));
        access.createGrant(current,CustomerKeys.digest(secret),CustomerKeys.valid(previous)?CustomerKeys.digest(previous):null,clock.instant(),expires);
        var result=view(new CustomerAccessRepository.Grant(current.appointmentId(),current.businessId(),current.professionalId(),current.generation(),expires));
        return new Opened(secret,result.sessionExpiresAt(),result);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public View read(String secret,UUID reservation){return view(grant(secret,reservation));}
    private View view(CustomerAccessRepository.Grant grant){
        var appointment=appointments.appointment(grant.businessId(),grant.appointmentId());
        Business business=catalog.business(grant.businessId());
        Instant until=CustomerKeys.validUntil(appointment.endsAt());
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(business.zoneId())));
        LocalDate suggested=appointment.startsAt().isAfter(clock.instant())?appointment.startsAt().atZone(ZoneId.of(business.zoneId())).toLocalDate():today.plusDays(1);
        return new View(appointment.id(),new BusinessDetails(business.name(),business.slug(),business.address(),business.zoneId()),appointment.reference(),
                appointment.serviceName(),appointment.professionalName(),appointment.customerName(),appointment.startsAt(),appointment.endsAt(),
                appointment.priceCents(),appointment.status(),appointment.version(),changeable(appointment),until,
                grant.expiresAt().isBefore(until)?grant.expiresAt():until,suggested,horizon,appointment.bookingPolicy(),
                changeable(appointment)&&appointment.bookingPolicy().allowsCancellation(appointment.startsAt(),clock.instant()),
                changeable(appointment)&&appointment.bookingPolicy().allowsReschedule(appointment.startsAt(),clock.instant()),
                appointment.bookingPolicy().cancelUntil(appointment.startsAt()),appointment.bookingPolicy().rescheduleUntil(appointment.startsAt()));
    }
    private boolean changeable(ScheduleRepository.Appointment appointment){return appointment.status().equals("BOOKED")&&appointment.startsAt().isAfter(clock.instant());}
    private void requireChangeable(ScheduleRepository.Appointment appointment){
        if(!changeable(appointment))throw BusinessException.conflict("Esta reserva não pode mais ser alterada pelo cliente. Confira a situação ou fale com o estabelecimento.");
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Availability availability(String secret,UUID reservation,LocalDate date){
        var grant=grant(secret,reservation);var appointment=appointments.appointment(grant.businessId(),grant.appointmentId());
        requireChangeable(appointment);requireRescheduleWindow(appointment);
        return schedule.customerAvailability(catalog.business(grant.businessId()),appointment,date);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public View cancel(String secret,UUID reservation,Change change){
        var grant=lockedGrant(secret,reservation);var appointment=appointments.appointment(grant.businessId(),grant.appointmentId());
        if(!appointment.status().equals("CANCELLED")){requireChangeable(appointment);
            if(!appointment.bookingPolicy().allowsCancellation(appointment.startsAt(),clock.instant()))throw BusinessException.conflict("O prazo de cancelamento online terminou. Fale com o estabelecimento.");
            schedule.cancel(catalog.business(grant.businessId()),grant.appointmentId(),change.version());}
        return view(grant);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public View move(String secret,UUID reservation,Move move){
        var grant=lockedGrant(secret,reservation);var appointment=appointments.appointment(grant.businessId(),grant.appointmentId());
        if(appointment.status().equals("BOOKED")&&move.version()==appointment.version()-1&&move.startsAt().toInstant().equals(appointment.startsAt()))return view(grant);
        requireChangeable(appointment);requireRescheduleWindow(appointment);
        appointment.bookingPolicy().requireBooking(move.startsAt().toInstant(),clock.instant());
        schedule.reschedule(catalog.business(grant.businessId()),grant.appointmentId(),new RescheduleRequest(move.startsAt(),move.version()));
        return view(grant);
    }
    private void requireRescheduleWindow(ScheduleRepository.Appointment appointment){
        if(!appointment.bookingPolicy().allowsReschedule(appointment.startsAt(),clock.instant()))
            throw BusinessException.conflict("O prazo de remarcação online terminou. Fale com o estabelecimento.");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void close(String secret,UUID reservation){if(CustomerKeys.valid(secret)){
        var grant=access.grant(CustomerKeys.digest(secret),clock.instant()).orElse(null);
        if(grant!=null&&grant.appointmentId().equals(reservation))access.closeGrant(CustomerKeys.digest(secret));
    }}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AccessState state(UUID business,UUID appointment){
        var current=appointments.appointment(business,appointment);var credentials=access.access(business,appointment).orElse(null);
        boolean valid=CustomerKeys.validUntil(current.endsAt()).isAfter(clock.instant());
        return new AccessState(keys.available()&&valid,valid&&credentials!=null&&credentials.digest()!=null&&credentials.revokedAt()==null,
                current.version(),CustomerKeys.validUntil(current.endsAt()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Issued issue(UUID business,UUID appointment,Issue issue){
        var initial=appointments.appointment(business,appointment);catalog.lockProfessional(business,initial.professionalId());
        var current=appointments.appointment(business,appointment);var existing=access.access(business,appointment).orElse(null);
        if(!keys.available())throw BusinessException.invalid("Configure a chave estável da instalação para oferecer acesso privado às reservas.");
        if(!CustomerKeys.validUntil(current.endsAt()).isAfter(clock.instant()))throw BusinessException.invalid("O período de acesso desta reserva terminou.");
        if(existing!=null&&existing.revokedAt()==null&&existing.digest()!=null&&issue.requestKey().equals(existing.issueKey()))
            return new Issued(keys.token(appointment,existing.generation()),current.version(),CustomerKeys.validUntil(current.endsAt()));
        requireVersion(current,issue.version());
        int generation=existing==null?1:Math.addExact(existing.generation(),1);
        // Rotated links must never be reconstructed by replaying the original public booking.
        if(generation==1)generation=2;
        String token=keys.token(appointment,generation);
        access.save(business,appointment,generation,CustomerKeys.digest(token),clock.instant(),issue.requestKey());
        appointments.advanceVersion(business,appointment,clock.instant());
        return new Issued(token,current.version()+1,CustomerKeys.validUntil(current.endsAt()));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AccessState revoke(UUID business,UUID appointment,Change change){
        var initial=appointments.appointment(business,appointment);catalog.lockProfessional(business,initial.professionalId());
        var current=appointments.appointment(business,appointment);var existing=access.access(business,appointment).orElse(null);
        if(existing==null||existing.revokedAt()!=null)return state(business,appointment);
        requireVersion(current,change.version());access.revoke(business,appointment,clock.instant());appointments.advanceVersion(business,appointment,clock.instant());
        return state(business,appointment);
    }
    private void requireVersion(ScheduleRepository.Appointment appointment,int version){
        if(version!=appointment.version())throw BusinessException.conflict("A reserva foi alterada em outra janela. Atualize os dados antes de confirmar.");
    }
}
