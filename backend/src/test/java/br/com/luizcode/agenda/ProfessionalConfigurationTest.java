package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import br.com.luizcode.agenda.team.TeamModels.*;
import br.com.luizcode.agenda.team.TeamRepository;
import br.com.luizcode.agenda.team.TeamService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ProfessionalConfigurationTest extends ApiTestBase {
    private static final String TEAM = "/api/admin/professionals";
    @Autowired TeamRepository teamRepository;
    @Autowired TeamService teamService;
    private final List<UUID> created = new ArrayList<>();

    @AfterEach void restoreFixtures() {
        jdbc.update("delete from appointment");
        jdbc.update("delete from time_block");
        for (UUID id : created) {
            jdbc.update("delete from weekly_hours where professional_id = ?", id);
            jdbc.update("delete from professional where id = ?", id);
        }
        jdbc.update("update professional set name = 'Lucas Almeida', specialty = 'Detalhes que fazem a diferença', active = TRUE, version = 0 where id = ?", PROFESSIONAL);
        jdbc.update("delete from weekly_hours where professional_id = ?", PROFESSIONAL);
        for (int day = 1; day <= 6; day++) for (int shift = 0; shift < 2; shift++)
            jdbc.update("insert into weekly_hours(id, business_id, professional_id, weekday, opens_at, closes_at) values (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), BUSINESS, PROFESSIONAL, day, LocalTime.of(shift == 0 ? 9 : 13,0), LocalTime.of(shift == 0 ? 12 : 18,0));
    }
    static WeeklyWindow window(int day, String start, String end) {
        return new WeeklyWindow(day, LocalTime.parse(start), LocalTime.parse(end));
    }
    UpdateProfessional update(List<WeeklyWindow> hours, boolean active, Integer version) {
        return new UpdateProfessional("Lucas Almeida", "Cortes e barba", active, version, hours,List.of(SERVICE));
    }
    org.springframework.test.web.servlet.ResultActions putConfig(UUID id, UpdateProfessional request) throws Exception {
        return mvc.perform(put(TEAM + "/" + id).with(user(owner())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }
    ProfessionalConfiguration create(String name) throws Exception {
        var response = mvc.perform(post(TEAM).with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateProfessional(name, "Atendimento de teste"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var result = mapper.readValue(response, ProfessionalConfiguration.class);
        created.add(result.id());
        return result;
    }

    @Test void teamOperationsRequireOwnerAndCsrf() throws Exception {
        mvc.perform(get(TEAM)).andExpect(status().isUnauthorized());
        mvc.perform(post(TEAM).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateProfessional("Pessoa", "Descrição")))).andExpect(status().isUnauthorized());
        mvc.perform(post(TEAM).with(user(owner())).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateProfessional("Pessoa", "Descrição")))).andExpect(status().isForbidden());
        mvc.perform(put(TEAM + "/" + PROFESSIONAL).with(user(owner())).contentType(MediaType.APPLICATION_JSON)
                .content(json(update(List.of(), true, 0)))).andExpect(status().isForbidden());
    }
    @Test void newProfessionalStartsWithoutAvailabilityAndBelongsToOwner() throws Exception {
        var person = create("  Pessoa de teste  ");
        assertThat(person.name()).isEqualTo("Pessoa de teste");
        assertThat(person.active()).isTrue();
        assertThat(person.version()).isZero();
        assertThat(person.hours()).isEmpty();
        assertThat(person.serviceIds()).isEmpty();
        assertThat(jdbc.queryForObject("select business_id from professional where id = ?", UUID.class, person.id())).isEqualTo(BUSINESS);
        mvc.perform(get(AVAILABILITY).param("professionalId", person.id().toString()).param("serviceId", SERVICE.toString())
                .param("date", DATE.toString())).andExpect(status().isConflict());
    }
    @Test void profileAndHoursAreSavedTogetherAndEnableBooking() throws Exception {
        var person = create("Novo profissional");
        putConfig(person.id(), update(List.of(window(2,"09:00","12:00")), true, 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.hours", hasSize(1)));
        var request = new BookingRequest(UUID.randomUUID(), person.id(), SERVICE, at("09:00"), "Cliente", "11999991234");
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated());
    }
    @Test void ownerCannotSeeOrEditOtherBusinessTeam() throws Exception {
        mvc.perform(get(TEAM).with(user(owner()))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(OTHER_PROFESSIONAL.toString()))));
        putConfig(OTHER_PROFESSIONAL, update(List.of(), true, 0)).andExpect(status().isNotFound());
        assertThat(teamRepository.find(OTHER_BUSINESS, OTHER_PROFESSIONAL).version()).isZero();
    }
    @Test void staleEditorCannotOverwriteNewConfiguration() throws Exception {
        putConfig(PROFESSIONAL, update(List.of(window(2,"09:00","12:00")), true, 0)).andExpect(status().isOk());
        putConfig(PROFESSIONAL, update(List.of(window(2,"13:00","18:00")), true, 0)).andExpect(status().isConflict());
        var current = teamRepository.find(BUSINESS, PROFESSIONAL);
        assertThat(current.version()).isEqualTo(1);
        assertThat(current.hours()).containsExactly(window(2,"09:00","12:00"));
    }

    static Stream<List<WeeklyWindow>> invalidHours() {
        return Stream.of(
                List.of(window(0,"09:00","12:00")), List.of(window(8,"09:00","12:00")),
                List.of(window(2,"12:00","09:00")), List.of(window(2,"09:00","09:00")),
                List.of(window(2,"09:05","12:00")), List.of(window(2,"09:00:01","12:00")),
                List.of(window(2,"09:00","12:05")), List.of(window(2,"09:00","12:00"),window(2,"11:00","13:00")),
                List.of(window(2,"09:00","10:00"),window(2,"10:00","11:00"),window(2,"11:00","12:00"),window(2,"13:00","14:00"),window(2,"15:00","16:00")),
                List.of(new WeeklyWindow(2,null,LocalTime.NOON)), Arrays.asList((WeeklyWindow)null));
    }
    @ParameterizedTest @MethodSource("invalidHours")
    void invalidWindowsAreRejectedWithoutPartialWrites(List<WeeklyWindow> hours) throws Exception {
        var before = teamRepository.find(BUSINESS, PROFESSIONAL);
        putConfig(PROFESSIONAL, update(hours, true, 0)).andExpect(status().isBadRequest());
        assertThat(teamRepository.find(BUSINESS, PROFESSIONAL)).isEqualTo(before);
    }
    @Test void missingVersionOrWeekIsRejected() throws Exception {
        putConfig(PROFESSIONAL, update(List.of(),true,null)).andExpect(status().isBadRequest());
        putConfig(PROFESSIONAL, update(null,true,0)).andExpect(status().isBadRequest());
        mvc.perform(post(TEAM).with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateProfessional(" ", "Descrição")))).andExpect(status().isBadRequest());
    }
    @Test void adjacentWindowsAreMergedAndAllowCrossBoundaryService() throws Exception {
        putConfig(PROFESSIONAL, update(List.of(window(2,"10:00","12:00"),window(2,"09:00","10:00")),true,0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hours",hasSize(1)));
        assertThat(teamRepository.find(BUSINESS, PROFESSIONAL).hours()).containsExactly(window(2,"09:00","12:00"));
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:45"))))
                .andExpect(status().isCreated());
    }
    @Test void shrinkingHoursCannotStrandConfirmedBookingOrPartiallyRenameProfile() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        var before = teamRepository.find(BUSINESS, PROFESSIONAL);
        var changed = new UpdateProfessional("Outro nome","Outra descrição",true,0,List.of(window(2,"09:00","09:30")),List.of(SERVICE));
        putConfig(PROFESSIONAL, changed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail",containsString("ficaria fora do expediente")));
        assertThat(teamRepository.find(BUSINESS, PROFESSIONAL)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from appointment where status = 'BOOKED'",Integer.class)).isEqualTo(1);
    }
    @Test void compatibleHoursMayChangeWithConfirmedBooking() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        putConfig(PROFESSIONAL, update(List.of(window(2,"09:00","11:00")),true,0)).andExpect(status().isOk());
    }
    @Test void cancelledOrPastBookingsDoNotPreventClosingWeek() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        UUID id = jdbc.queryForObject("select id from appointment",UUID.class);
        schedule.cancel(catalog.business(BUSINESS),id);
        putConfig(PROFESSIONAL, update(List.of(),true,0)).andExpect(status().isOk());
        // A historical, still-confirmed record must not freeze future configuration.
        jdbc.update("update appointment set status = 'BOOKED', starts_at = ?, ends_at = ? where id = ?",
                at("09:00").minusDays(3),at("09:45").minusDays(3),id);
        putConfig(PROFESSIONAL, update(List.of(),true,1)).andExpect(status().isOk());
    }
    @Test void deactivationPreservesHistoryAndRejectsNewBookingsUntilReactivation() throws Exception {
        var original = teamRepository.find(BUSINESS, PROFESSIONAL);
        putConfig(PROFESSIONAL, update(original.hours(),false,0)).andExpect(status().isOk());
        mvc.perform(get("/api/public/" + SLUG + "/catalog")).andExpect(jsonPath("$.professionals[*].id",not(hasItem(PROFESSIONAL.toString()))));
        mvc.perform(get(AVAILABILITY).param("professionalId",PROFESSIONAL.toString()).param("serviceId",SERVICE.toString())
                .param("date",DATE.toString())).andExpect(status().isNotFound());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:00"))))
                .andExpect(status().isNotFound());
        putConfig(PROFESSIONAL, update(original.hours(),true,1)).andExpect(status().isOk());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:00"))))
                .andExpect(status().isCreated());
    }
    @Test void futureAndOngoingAppointmentsPreventDeactivation() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        var original = teamRepository.find(BUSINESS, PROFESSIONAL);
        putConfig(PROFESSIONAL, update(original.hours(),false,0)).andExpect(status().isConflict());
        jdbc.update("update appointment set starts_at = ?, ends_at = ?",
                OffsetDateTime.parse("2030-06-03T09:45:00Z"), OffsetDateTime.parse("2030-06-03T10:30:00Z"));
        putConfig(PROFESSIONAL, update(original.hours(),false,0)).andExpect(status().isConflict());
        assertThat(teamRepository.find(BUSINESS, PROFESSIONAL)).isEqualTo(original);
    }
    @Test void blocksAreRetainedWhenWeekIsEdited() throws Exception {
        schedule.block(catalog.business(BUSINESS),new BlockRequest(PROFESSIONAL,at("10:00"),at("11:00"),"Compromisso"));
        putConfig(PROFESSIONAL,update(List.of(window(2,"09:00","12:00")),true,0)).andExpect(status().isOk());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("10:00"))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from time_block",Integer.class)).isEqualTo(1);
    }
    @Test void simultaneousBookingAndClosingHoursCannotCreateInvalidSchedule() throws Exception {
        var outcomes = race(index -> {
            try {
                if (index == 0) schedule.book(catalog.business(BUSINESS), request("09:00"));
                else teamService.update(BUSINESS,PROFESSIONAL,update(List.of(window(2,"13:00","18:00")),true,0));
                return index == 0 ? "booked" : "configured";
            } catch (BusinessException ex) { assertThat(ex.status().value()).isIn(400,409); return "rejected"; }
        });
        assertThat(outcomes).filteredOn("rejected"::equals).hasSize(1);
        boolean booked = jdbc.queryForObject("select count(*) from appointment",Integer.class) == 1;
        var current = teamRepository.find(BUSINESS, PROFESSIONAL);
        assertThat(current.version()).isEqualTo(booked ? 0 : 1);
        if (!booked) assertThat(current.hours()).containsExactly(window(2,"13:00","18:00"));
    }
    @Test void simultaneousBookingAndDeactivationCannotLeaveFutureBookingInactive() throws Exception {
        var hours = teamRepository.find(BUSINESS, PROFESSIONAL).hours();
        var outcomes = race(index -> {
            try {
                if (index == 0) schedule.book(catalog.business(BUSINESS),request("09:00"));
                else teamService.update(BUSINESS,PROFESSIONAL,update(hours,false,0));
                return "saved";
            } catch (BusinessException ex) { assertThat(ex.status().value()).isIn(404,409); return "rejected"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("saved","rejected");
        if (jdbc.queryForObject("select count(*) from appointment",Integer.class) == 1)
            assertThat(teamRepository.find(BUSINESS,PROFESSIONAL).active()).isTrue();
    }
    private List<String> race(java.util.function.IntFunction<String> action) throws Exception {
        var workers = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i=0;i<2;i++) { int index=i; futures.add(workers.submit(() -> {
                ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                return action.apply(index);
            })); }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS));
        } finally { start.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
}
