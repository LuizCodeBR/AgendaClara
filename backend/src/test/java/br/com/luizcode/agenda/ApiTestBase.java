package br.com.luizcode.agenda;

import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.identity.AgendaUser;
import br.com.luizcode.agenda.scheduling.ScheduleModels.BookingRequest;
import br.com.luizcode.agenda.scheduling.ScheduleService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = AgendaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestBase.TestClock.class)
abstract class ApiTestBase {
    static final String SLUG = "barbearia-horizonte";
    static final UUID BUSINESS = id(SLUG);
    static final UUID OTHER_BUSINESS = id("estudio-aurora");
    static final UUID PROFESSIONAL = id(SLUG + "-professional-0");
    static final UUID SECOND_PROFESSIONAL = id(SLUG + "-professional-1");
    static final UUID SERVICE = id(SLUG + "-service-0");
    static final UUID OTHER_SERVICE = id("estudio-aurora-service-0");
    static final UUID OTHER_PROFESSIONAL = id("estudio-aurora-professional-0");
    static final LocalDate DATE = LocalDate.of(2030, 6, 4);
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogRepository catalog;
    @Autowired ScheduleService schedule;

    @BeforeEach void cleanCalendar() {
        jdbc.update("delete from date_hours_window");
        jdbc.update("delete from date_hours");
        jdbc.update("delete from appointment");
        jdbc.update("delete from time_block");
    }
    static UUID id(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)); }
    static OffsetDateTime at(String time) { return DATE.atTime(LocalTime.parse(time)).atZone(ZONE).toOffsetDateTime(); }
    static AgendaUser owner() { return new AgendaUser(id(SLUG + "-owner"), BUSINESS, "Marina", "marina@demo.agendaclara.dev", "unused"); }
    BookingRequest request(String time) {
        return new BookingRequest(UUID.randomUUID(), PROFESSIONAL, SERVICE, at(time), "Cliente Teste", "(11) 99999-1234");
    }
    String json(Object value) { return mapper.writeValueAsString(value); }
    static final String BOOKINGS = "/api/public/" + SLUG + "/bookings";
    static final String AVAILABILITY = "/api/public/" + SLUG + "/availability";

    @TestConfiguration
    static class TestClock {
        @Bean @Primary Clock deterministicClock() { return Clock.fixed(Instant.parse("2030-06-03T10:00:00Z"), ZoneOffset.UTC); }
    }
}
