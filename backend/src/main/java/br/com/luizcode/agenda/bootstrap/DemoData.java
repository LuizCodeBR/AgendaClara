package br.com.luizcode.agenda.bootstrap;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;
    public DemoData(JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc; this.encoder = encoder; this.clock = clock;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {

        seed("barbearia-horizonte", "Barbearia Horizonte", "Barbearia", "Rua das Palmeiras, 120 · Centro",
                "Marina", "marina@demo.agendaclara.dev", new String[]{"Lucas Almeida", "Bruno Costa"},
                new String[]{"Corte clássico", "Barba & acabamento", "Corte + barba"}, new int[]{45, 30, 60},
                new int[]{5500, 3500, 8500});
        seed("estudio-aurora", "Estúdio Aurora", "Beleza e bem-estar", "Avenida do Sol, 48 · Vila Nova",
                "Rafael", "rafael@demo.agendaclara.dev", new String[]{"Ana Lima", "Clara Santos"},
                new String[]{"Design de sobrancelhas", "Manicure", "Ritual de cuidados"}, new int[]{30, 45, 60},
                new int[]{4000, 4500, 9000});
        System.out.println("DEMONSTRAÇÃO: dados e contas são fictícios. Contas compartilhadas para testes; não use dados reais.");
    }
    private void seed(String slug, String name, String category, String address, String owner, String email,
                      String[] professionals, String[] services, int[] durations, int[] prices) {
        if (jdbc.queryForObject("select count(*) from business where slug=?",Integer.class,slug)>0) return;
        UUID businessId = id(slug);
        jdbc.update("insert into business (id, slug, name, category, zone_id, address) values (?, ?, ?, ?, ?, ?)",
                businessId, slug, name, category, "America/Sao_Paulo", address);
        jdbc.update("insert into app_user (id, business_id, email, password_hash, display_name) values (?, ?, ?, ?, ?)",
                id(slug + "-owner"), businessId, email, encoder.encode("Demo@12345"), owner);
        for (int i = 0; i < professionals.length; i++) {
            UUID professionalId = id(slug + "-professional-" + i);
            jdbc.update("insert into professional (id, business_id, name, specialty) values (?, ?, ?, ?)",
                    professionalId, businessId, professionals[i], i == 0 ? "Detalhes que fazem a diferença" : "Cuidado em cada atendimento");
            for (int weekday = 1; weekday <= 6; weekday++) {
                for (int shift = 0; shift < 2; shift++) {
                    jdbc.update("insert into weekly_hours (id, business_id, professional_id, weekday, opens_at, closes_at) values (?, ?, ?, ?, ?, ?)",
                            id(slug + "-" + i + "-" + weekday + "-" + shift), businessId, professionalId, weekday,
                            LocalTime.of(shift == 0 ? 9 : 13, 0), LocalTime.of(shift == 0 ? 12 : 18, 0));
                }
            }
        }
        for (int i = 0; i < services.length; i++) {
            jdbc.update("insert into service_offering (id, business_id, name, description, duration_minutes, price_cents) values (?, ?, ?, ?, ?, ?)",
                    id(slug + "-service-" + i), businessId, services[i],
                    i == 0 ? "Seu momento de cuidado, no seu ritmo." : "Atendimento com atenção a cada detalhe.",
                    durations[i], prices[i]);
        }
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate date = LocalDate.now(clock.withZone(zone)).plusDays(1);
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) date = date.plusDays(1);
        seedAppointment(slug, businessId, 0, 0, services[0], prices[0], date.atTime(9, 30).atZone(zone).toInstant(), durations[0], "João Exemplo", "11999990001");
        seedAppointment(slug, businessId, 1, 1, services[1], prices[1], date.atTime(11, 0).atZone(zone).toInstant(), durations[1], "Maria Exemplo", "11999990002");
        seedAppointment(slug, businessId, 0, 2, services[2], prices[2], date.atTime(15, 0).atZone(zone).toInstant(), durations[2], "Pedro Exemplo", "11999990003");
    }
    private void seedAppointment(String slug, UUID businessId, int professional, int service, String serviceName,
                                 int price, Instant start, int duration, String customer, String phone) {
        UUID appointmentId = id(slug + "-sample-" + service);
        jdbc.update("""
                insert into appointment
                (id, business_id, professional_id, service_id, service_name, price_cents, customer_name, customer_phone,
                 starts_at, ends_at, status, reference, request_key, request_fingerprint, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BOOKED', ?, ?, ?, ?, ?)
                """, appointmentId, businessId, id(slug + "-professional-" + professional), id(slug + "-service-" + service),
                serviceName, price, customer, phone, start.atOffset(ZoneOffset.UTC),
                start.plusSeconds(duration * 60L).atOffset(ZoneOffset.UTC),
                appointmentId.toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT),
                id(slug + "-sample-key-" + service), "demo", clock.instant().atOffset(ZoneOffset.UTC),
                clock.instant().atOffset(ZoneOffset.UTC));
    }
    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
