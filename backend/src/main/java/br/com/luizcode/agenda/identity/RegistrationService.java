package br.com.luizcode.agenda.identity;

import static br.com.luizcode.agenda.identity.RegistrationModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private static final Set<String> ZONES = Set.of("America/Sao_Paulo", "America/Manaus", "America/Rio_Branco", "America/Noronha");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final boolean enabled, publicDemo;
    public RegistrationService(JdbcTemplate jdbc, PasswordEncoder passwords,
            @Value("${app.registration.enabled:true}") boolean enabled,
            @Value("${app.demo.public:false}") boolean publicDemo) {
        this.jdbc=jdbc; this.passwords=passwords; this.enabled=enabled; this.publicDemo=publicDemo;
    }

    @Transactional
    public RegistrationOutcome register(RegisterRequest request) {
        validate(request);
        var previous = previous(request);
        if (previous.isPresent()) return new RegistrationOutcome(previous.get(), false);
        UUID businessId=UUID.randomUUID();
        String prefix=Normalizer.normalize(request.businessName(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (prefix.isEmpty()) prefix="empresa";
        if (prefix.length()>40) prefix=prefix.substring(0,40).replaceAll("-$", "");
        String slug=prefix + "-" + businessId.toString().replace("-", "");
        jdbc.update("insert into business (id,slug,name,category,zone_id,address,public_booking_enabled) values (?,?,?,?,?,?,false)",
                businessId,slug,request.businessName(),request.category(),request.zoneId(),request.address());
        jdbc.update("insert into app_user (id,business_id,email,password_hash,display_name,registration_key,email_verification_required) values (?,?,?,?,?,?,true)",
                UUID.randomUUID(),businessId,request.email(),passwords.encode(request.password()),request.ownerName(),request.requestKey());
        return new RegistrationOutcome(new RegistrationReceipt(businessId,request.businessName(),slug,request.email()), true);
    }

    // Called outside the failed write transaction: PostgreSQL cannot query an aborted transaction.
    @Transactional(readOnly=true)
    public RegistrationReceipt replay(RegisterRequest request) {
        validate(request);
        return previous(request).orElseThrow(RegistrationService::conflict);
    }

    private Optional<RegistrationReceipt> previous(RegisterRequest request) {
        var matches=jdbc.query("""
                select b.id,b.slug,b.name,b.category,b.address,b.zone_id,u.email,u.display_name,u.password_hash
                from app_user u join business b on b.id=u.business_id where u.registration_key=?
                """, (rs,row) -> {
                    boolean same=request.businessName().equals(rs.getString("name"))
                            && request.category().equals(rs.getString("category"))
                            && request.address().equals(rs.getString("address"))
                            && request.zoneId().equals(rs.getString("zone_id"))
                            && request.ownerName().equals(rs.getString("display_name"))
                            && request.email().equals(rs.getString("email"))
                            && passwords.matches(request.password(),rs.getString("password_hash"));
                    if (!same) throw conflict();
                    return new RegistrationReceipt(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("slug"),rs.getString("email"));
                },request.requestKey());
        return matches.stream().findFirst();
    }

    private void validate(RegisterRequest request) {
        if (!enabled) throw BusinessException.invalid("O cadastro de novas empresas está indisponível neste ambiente.");
        if (publicDemo && !request.demoAcknowledged())
            throw BusinessException.invalid("Confirme que usará apenas dados fictícios nesta demonstração temporária.");
        if (!ZONES.contains(request.zoneId())) throw BusinessException.invalid("Escolha um dos fusos horários disponíveis.");
        PasswordPolicy.validate(request.password());
        if (request.email().endsWith("@demo.agendaclara.dev"))
            throw BusinessException.invalid("O domínio das contas de demonstração é reservado.");
    }
    static BusinessException conflict() {
        return BusinessException.conflict("Não foi possível concluir este cadastro. Se você já tem uma conta, entre no painel; caso contrário, confira os dados e tente novamente.");
    }
}
