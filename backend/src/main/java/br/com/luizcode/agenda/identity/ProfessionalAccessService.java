package br.com.luizcode.agenda.identity;

import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ProfessionalAccessService {
    public record Invite(@NotBlank @Email @Size(max=180) String email,@NotNull @Min(0) Integer version){
        public Invite{email=email==null?null:email.strip().toLowerCase(Locale.ROOT);}
    }
    public record Version(@NotNull @Min(0) Integer version){}
    public record Token(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){
        @Override public String toString(){return "Token[protected]";}
    }
    public record Accept(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotNull @Size(max=144) String password){
        @Override public String toString(){return "Accept[protected]";}
    }
    public record State(UUID professionalId,String name,boolean active,int version,String email,boolean enabled,Instant inviteExpiresAt){}
    public record Issued(String token,Instant expiresAt){
        @Override public String toString(){return "Issued[protected]";}
    }
    public record Preview(String businessName,String professionalName,String email,Instant expiresAt){}
    public record Accepted(String mailboxProof){
        @Override public String toString(){return "Accepted[protected]";}
    }
    private record Pending(UUID professional,UUID business,String email,Instant expires){}
    private final JdbcTemplate jdbc;
    private final CatalogRepository catalog;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final AccountAccessService accounts;
    public ProfessionalAccessService(JdbcTemplate jdbc,CatalogRepository catalog,PasswordEncoder passwords,Clock clock,AccountAccessService accounts){
        this.jdbc=jdbc;this.catalog=catalog;this.passwords=passwords;this.clock=clock;this.accounts=accounts;
    }
    @Transactional(readOnly=true)
    public List<State> list(UUID business){
        var result=jdbc.query("""
            select p.id,p.name,p.active,coalesce(a.version,0),coalesce(u.email,a.invite_email,''),coalesce(u.enabled,false),a.invite_expires_at
            from professional p left join professional_access a on a.professional_id=p.id
            left join app_user u on u.professional_id=p.id and u.business_id=p.business_id
            where p.business_id=? order by p.name,p.id limit 201
            """,(r,n)->new State(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getInt(4),r.getString(5),r.getBoolean(6),instant(r.getObject(7,OffsetDateTime.class))),business);
        if(result.size()>200)throw BusinessException.invalid("Consulte o responsável técnico: a equipe excede o limite de 200 acessos.");
        return result;
    }
    private void lock(UUID business,UUID professional,int version){
        catalog.lockProfessional(business,professional);
        var versions=jdbc.query("select version from professional_access where business_id=? and professional_id=?",(r,n)->r.getInt(1),business,professional);
        if(versions.isEmpty())jdbc.update("insert into professional_access(professional_id,business_id) values(?,?)",professional,business);
        if((versions.isEmpty()?0:versions.getFirst())!=version)throw BusinessException.conflict("O acesso mudou em outra janela. Atualize a equipe antes de continuar.");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Issued invite(UUID business,UUID professional,Invite request){
        lock(business,professional,request.version());
        if(!jdbc.queryForObject("select active from professional where id=? and business_id=?",Boolean.class,professional,business))
            throw BusinessException.conflict("Ative o profissional antes de liberar seu acesso.");
        if(request.email().endsWith("@demo.agendaclara.dev"))throw BusinessException.invalid("O domínio das contas compartilhadas é reservado.");
        var current=jdbc.query("select email,enabled from app_user where professional_id=? for update",(r,n)->Map.entry(r.getString(1),r.getBoolean(2)),professional);
        if(!current.isEmpty()&&(current.getFirst().getValue()||!current.getFirst().getKey().equals(request.email())))
            throw BusinessException.conflict("Revogue o acesso antes de convidar novamente. Uma conta existente deve manter o mesmo e-mail.");
        if(jdbc.queryForObject("select count(*) from app_user where email=? and (professional_id is null or professional_id<>?)",Integer.class,request.email(),professional)>0)
            throw unavailable();
        String token=SecretTokens.create();Instant expires=clock.instant().plus(Duration.ofHours(24)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("update professional_access set version=version+1,invite_digest=?,invite_email=?,invite_expires_at=? where professional_id=?",
            SecretTokens.digest(token),request.email(),utc(expires),professional);
        return new Issued(token,expires);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void revoke(UUID business,UUID professional,int version){
        lock(business,professional,version);
        jdbc.update("update app_user set enabled=false,credentials_version=credentials_version+1,demo_mailbox_digest=null,demo_mailbox_expires_at=null where professional_id=? and business_id=?",professional,business);
        jdbc.update("update account_action set used_at=? where user_id in (select id from app_user where professional_id=? and business_id=?) and used_at is null",utc(clock.instant()),professional,business);
        jdbc.update("update professional_access set version=version+1,invite_digest=null,invite_email=null,invite_expires_at=null where professional_id=?",professional);
    }
    private Pending pending(String token){
        if(!SecretTokens.wellFormed(token))throw invalid();
        var found=jdbc.query("select professional_id,business_id,invite_email,invite_expires_at from professional_access where invite_digest=?",
            (r,n)->new Pending(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),instant(r.getObject(4,OffsetDateTime.class))),SecretTokens.digest(token));
        if(found.isEmpty()||!found.getFirst().expires().isAfter(clock.instant()))throw invalid();
        return found.getFirst();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview preview(String token){
        var p=pending(token);
        if(!jdbc.queryForObject("select active from professional where id=? and business_id=?",Boolean.class,p.professional(),p.business()))throw invalid();
        return new Preview(catalog.business(p.business()).name(),jdbc.queryForObject("select name from professional where id=?",String.class,p.professional()),p.email(),p.expires());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Accepted accept(Accept request){
        PasswordPolicy.validate(request.password());
        var initial=pending(request.token());
        catalog.lockProfessional(initial.business(),initial.professional());
        var p=pending(request.token());
        if(!jdbc.queryForObject("select active from professional where id=?",Boolean.class,p.professional()))throw invalid();
        var users=jdbc.query("select id,email,enabled from app_user where professional_id=? for update",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3)},p.professional());
        String hash=passwords.encode(request.password());
        try{
            if(users.isEmpty())jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name,role,professional_id) select ?,business_id,?,?,name,'PROFESSIONAL',id from professional where id=?",
                UUID.randomUUID(),p.email(),hash,p.professional());
            else{
                var u=users.getFirst();
                if((boolean)u[2]||!p.email().equals(u[1]))throw invalid();
                jdbc.update("update app_user set enabled=true,password_hash=?,credentials_version=credentials_version+1 where id=?",hash,u[0]);
                jdbc.update("update account_action set used_at=? where user_id=? and used_at is null",utc(clock.instant()),u[0]);
            }
        }catch(DataIntegrityViolationException e){throw unavailable();}
        if(!p.expires().isAfter(clock.instant()))throw invalid();
        jdbc.update("update professional_access set version=version+1,invite_digest=null,invite_email=null,invite_expires_at=null where professional_id=?",p.professional());
        // The mailbox belongs to this activation, including during a concurrent revoke/reinvite.
        return new Accepted(accounts.openDemoMailbox(p.email(),null));
    }
    /** Called after the professional mutex: revocation and an in-flight write have a defined winner. */
    public void requireCurrent(AgendaUser user,boolean write){
        if(write)br.com.luizcode.agenda.shared.WriteTransaction.require();
        var rows=jdbc.query("""
            select u.credentials_version,u.enabled,u.role,u.professional_id,p.active from app_user u
            join professional p on p.id=u.professional_id and p.business_id=u.business_id
            where u.id=? and u.business_id=?
            """+(write?" for update":""),(r,n)->r.getInt(1)==user.credentialsVersion()&&r.getBoolean(2)&&r.getString(3).equals("PROFESSIONAL")&&Objects.equals(r.getObject(4,UUID.class),user.professionalId())&&r.getBoolean(5),user.id(),user.businessId());
        if(rows.isEmpty()||!rows.getFirst())throw new BusinessException(HttpStatus.FORBIDDEN,"Seu acesso profissional foi revogado ou atualizado. Entre novamente.");
    }
    private static Instant instant(OffsetDateTime t){return t==null?null:t.toInstant();}
    private static OffsetDateTime utc(Instant t){return t.atOffset(ZoneOffset.UTC);}
    private static BusinessException invalid(){return BusinessException.invalid("Convite inválido, expirado, revogado ou já utilizado. Solicite outro ao responsável.");}
    private static BusinessException unavailable(){return BusinessException.conflict("Não foi possível vincular este e-mail. Confira os dados ou utilize outra conta.");}
}
