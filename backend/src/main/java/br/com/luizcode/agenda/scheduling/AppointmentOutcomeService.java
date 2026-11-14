package br.com.luizcode.agenda.scheduling;

import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.identity.AgendaUser;
import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AppointmentOutcomeService {
    public enum Result {COMPLETED,NO_SHOW}
    public record Change(@NotNull Result status,@NotNull @Min(0) Integer version,@NotNull UUID requestKey,@Size(max=500) String reason){}
    private final ScheduleRepository appointments;
    private final CatalogRepository catalog;
    private final AppointmentHistory history;
    private final Clock clock;
    public AppointmentOutcomeService(ScheduleRepository appointments,CatalogRepository catalog,AppointmentHistory history,Clock clock){
        this.appointments=appointments;this.catalog=catalog;this.history=history;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AppointmentHistory.Page history(UUID business,UUID id,Integer before){
        appointments.appointment(business,id);
        return history.page(business,id,before);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ScheduleModels.AppointmentView change(AgendaUser user,UUID id,Change request){
        UUID business=user.businessId();
        var initial=appointments.appointment(business,id);
        catalog.lockProfessional(business,initial.professionalId());
        var current=appointments.appointment(business,id);
        String reason=request.reason()==null?"":request.reason().strip();
        var previous=history.attempt(business,request.requestKey());
        if(previous.isPresent()){
            var p=previous.get();
            if(!p.appointmentId().equals(id)||p.expectedVersion()!=request.version()||!p.status().equals(request.status().name())||!p.reason().equals(reason))
                throw BusinessException.conflict("Esta tentativa já foi usada com outros dados.");
            return current.view();
        }
        if(current.version()!=request.version())throw BusinessException.conflict("A reserva foi alterada em outra janela. Atualize os dados antes de registrar o resultado.");
        if(current.status().equals("CANCELLED"))throw BusinessException.conflict("Uma reserva cancelada não pode receber resultado de atendimento.");
        boolean correction=!current.status().equals("BOOKED");
        if(correction&&current.status().equals(request.status().name()))throw BusinessException.conflict("Este resultado já foi registrado. Atualize o atendimento.");
        if(correction&&reason.length()<5)throw BusinessException.invalid("Explique a correção em pelo menos 5 caracteres.");
        Instant now=clock.instant();
        if(current.endsAt().isAfter(now))throw BusinessException.conflict("Registre o resultado somente após o término previsto do atendimento.");
        appointments.result(business,id,request.status().name(),now);
        var after=appointments.appointment(business,id);
        history.append(current,after,correction?"CORRECTION":"OUTCOME",AppointmentHistory.Actor.owner(user),reason,now,request.requestKey(),request.version());
        return after.view();
    }
}
