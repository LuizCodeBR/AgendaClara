package br.com.luizcode.agenda.identity;

import static br.com.luizcode.agenda.identity.AccessModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAccessService {
    private record Account(UUID id,String email,boolean verified,String mailbox,Instant mailboxExpires) {}
    private record Action(UUID userId,String purpose,Instant expires,Instant used,boolean simulated) {}
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final MailSettings settings;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    public AccountAccessService(JdbcTemplate jdbc,PasswordEncoder passwords,MailSettings settings,Clock clock,ApplicationEventPublisher events) {
        this.jdbc=jdbc; this.passwords=passwords; this.settings=settings; this.clock=clock; this.events=events;
    }
    private List<Account> accounts(String clause,Object value,boolean lock) {
        return jdbc.query("select id,email,email_verified_at,demo_email_verified_at,demo_mailbox_digest,demo_mailbox_expires_at from app_user where "+clause+(lock?" for update":""),
                (rs,row)->new Account(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3)!=null||(settings.demo()&&rs.getObject(4)!=null),
                        rs.getString(5),rs.getObject(6,OffsetDateTime.class)==null?null:rs.getObject(6,OffsetDateTime.class).toInstant()),value);
    }
    private static boolean shared(Account account) { return account.email().endsWith("@demo.agendaclara.dev"); }
    private boolean ownsMailbox(Account account,String proof) {
        return SecretTokens.wellFormed(proof) && account.mailbox()!=null && account.mailboxExpires()!=null
                && account.mailboxExpires().isAfter(clock.instant()) && account.mailbox().equals(SecretTokens.digest(proof));
    }

    @Transactional
    public String openDemoMailbox(String email,String currentProof) {
        if(!settings.demo()) return null;
        var found=accounts("email=?",email,true);
        if(found.isEmpty()||shared(found.getFirst())) return null;
        Account account=found.getFirst();
        if(ownsMailbox(account,currentProof)) return currentProof;
        String proof=SecretTokens.create();
        jdbc.update("update app_user set demo_mailbox_digest=?,demo_mailbox_expires_at=? where id=?",
                SecretTokens.digest(proof),utc(clock.instant().plusSeconds(3600)),account.id());
        return proof;
    }
    @Transactional(readOnly=true)
    public String mailboxDigest(String proof) {
        if(!settings.demo()||!SecretTokens.wellFormed(proof)) return null;
        var found=accounts("demo_mailbox_digest=?",SecretTokens.digest(proof),false);
        return found.isEmpty()||!ownsMailbox(found.getFirst(),proof)?null:found.getFirst().mailbox();
    }
    @Transactional
    public void request(String email,String purpose,String proof,String origin) {
        if(!settings.available()) return;
        var found=accounts("email=?",email,true);
        if(found.isEmpty()) return;
        Account account=found.getFirst();
        if(shared(account)||("VERIFY".equals(purpose)&&account.verified())||(settings.demo()&&!ownsMailbox(account,proof))) return;
        Instant now=clock.instant();
        var recent=jdbc.query("select created_at from account_action where user_id=? and purpose=? order by created_at desc limit 1",
                (rs,row)->rs.getObject(1,OffsetDateTime.class).toInstant(),account.id(),purpose);
        if(!recent.isEmpty()&&recent.getFirst().plusSeconds(60).isAfter(now)) return;
        jdbc.update("delete from account_action where user_id=? and expires_at<?",account.id(),utc(now.minus(Duration.ofDays(7))));
        jdbc.update("update account_action set used_at=? where user_id=? and purpose=? and used_at is null",utc(now),account.id(),purpose);
        String token=SecretTokens.create(); UUID id=UUID.randomUUID();
        Instant expires=now.plus("VERIFY".equals(purpose)?Duration.ofHours(24):Duration.ofMinutes(15));
        jdbc.update("insert into account_action(id,user_id,purpose,token_digest,created_at,expires_at,simulated) values(?,?,?,?,?,?,?)",
                id,account.id(),purpose,SecretTokens.digest(token),utc(now),utc(expires),settings.demo());
        String route="VERIFY".equals(purpose)?"confirmar-email":"redefinir-senha";
        String subject="VERIFY".equals(purpose)?"Confirme seu e-mail na Agenda Clara":"Redefina sua senha na Agenda Clara";
        String text="VERIFY".equals(purpose)?
                "Você solicitou uma conta na Agenda Clara. Abra o link e confirme seu e-mail para publicar sua página de reservas. O link vale por 24 horas. Se não foi você, ignore esta mensagem.":
                "Abra o link para escolher uma nova senha. Ele vale por 15 minutos e só pode ser usado uma vez. Se não solicitou a troca, ignore esta mensagem; sua senha continua igual.";
        events.publishEvent(new OutgoingMail(id,account.mailbox(),purpose,account.email(),subject,text,
                origin+"/#/"+route+"?token="+token,expires));
    }

    @Transactional
    public void verify(String token) {
        Action action=consume(token,"VERIFY");
        String column=action.simulated()?"demo_email_verified_at":"email_verified_at";
        jdbc.update("update app_user set "+column+"=? where id=?",utc(clock.instant()),action.userId());
    }
    @Transactional
    public void reset(String token,String password) {
        PasswordPolicy.validate(password);
        Action action=consume(token,"RESET"); Instant now=clock.instant();
        String column=action.simulated()?"demo_email_verified_at":"email_verified_at";
        jdbc.update("update app_user set password_hash=?,credentials_version=credentials_version+1,"+column+"=? where id=?",
                passwords.encode(password),utc(now),action.userId());
        jdbc.update("update account_action set used_at=? where user_id=? and used_at is null",utc(now),action.userId());
        Account account=accounts("id=?",action.userId(),false).getFirst();
        if(settings.available()) events.publishEvent(new OutgoingMail(UUID.randomUUID(),account.mailbox(),"CHANGED",account.email(),
                "Sua senha da Agenda Clara foi alterada",
                "A senha da sua conta foi alterada. As sessões anteriores perderam o acesso. Se não foi você, solicite uma nova recuperação pelo site. Esta mensagem não contém sua senha.","",now.plus(Duration.ofHours(1))));
    }
    private Action consume(String token,String purpose) {
        if(!SecretTokens.wellFormed(token)) throw invalid();
        String digest=SecretTokens.digest(token);
        var initial=actions(digest);
        if(initial.isEmpty()) throw invalid();
        var owner=accounts("id=?",initial.getFirst().userId(),true);
        if(owner.isEmpty()||shared(owner.getFirst())) throw invalid();
        var remaining=actions(digest); if(remaining.isEmpty()) throw invalid();
        Action action=remaining.getFirst();
        if((action.simulated()&&!settings.demo())||!action.purpose().equals(purpose)||action.used()!=null||!action.expires().isAfter(clock.instant())) throw invalid();
        jdbc.update("update account_action set used_at=? where token_digest=?",utc(clock.instant()),digest);
        return action;
    }
    private List<Action> actions(String digest) {
        return jdbc.query("select user_id,purpose,expires_at,used_at,simulated from account_action where token_digest=?",
                (rs,row)->new Action(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,OffsetDateTime.class).toInstant(),
                        rs.getObject(4,OffsetDateTime.class)==null?null:rs.getObject(4,OffsetDateTime.class).toInstant(),rs.getBoolean(5)),digest);
    }
    private static OffsetDateTime utc(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private static BusinessException invalid() { return BusinessException.invalid("Link inválido, expirado ou já utilizado. Solicite outro link."); }
}
