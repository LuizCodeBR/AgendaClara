package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static br.com.luizcode.agenda.hours.DateHoursModels.*;
import static br.com.luizcode.agenda.team.TeamModels.*;
import br.com.luizcode.agenda.hours.*;
import br.com.luizcode.agenda.team.*;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

class DateHoursTest extends ApiTestBase {
    @Autowired TeamService team;
    @Autowired TeamRepository people;
    @Autowired DateHoursService dates;
    UUID person;
    String endpoint;

    @BeforeEach void prepareProfessional() {
        var created=team.create(BUSINESS,new CreateProfessional("Profissional das exceções","Teste"));
        person=created.id();
        endpoint="/api/admin/professionals/"+person+"/exceptions";
        var windows=new ArrayList<WeeklyWindow>();
        for(int day=1;day<=6;day++) {
            windows.add(new WeeklyWindow(day,LocalTime.of(9,0),LocalTime.NOON));
            windows.add(new WeeklyWindow(day,LocalTime.of(13,0),LocalTime.of(18,0)));
        }
        team.update(BUSINESS,person,new UpdateProfessional(created.name(),created.specialty(),true,0,windows));
    }
    @AfterEach void cleanProfessional() {
        jdbc.update("delete from date_hours_window"); jdbc.update("delete from date_hours");
        jdbc.update("delete from appointment"); jdbc.update("delete from time_block");
        if(person!=null) {
            jdbc.update("delete from weekly_hours where professional_id=?",person);
            jdbc.update("delete from professional where id=?",person);
        }
    }
    int version() { return people.find(BUSINESS,person).version(); }
    DayWindow window(String start,String end) { return new DayWindow(LocalTime.parse(start),LocalTime.parse(end)); }
    SaveException draft(List<DayWindow> hours) { return new SaveException(version(),"Motivo privado",hours); }
    BookingRequest booking(LocalDate date,String time) {
        return new BookingRequest(UUID.randomUUID(),person,SERVICE,date.atTime(LocalTime.parse(time)).atZone(ZONE).toOffsetDateTime(),
                "Cliente de teste","11999991234");
    }
    org.springframework.test.web.servlet.ResultActions save(LocalDate date,SaveException request) throws Exception {
        return mvc.perform(put(endpoint+"/"+date).with(user(owner())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }
    org.springframework.test.web.servlet.ResultActions available(LocalDate date) throws Exception {
        return mvc.perform(get(AVAILABILITY).param("professionalId",person.toString()).param("serviceId",SERVICE.toString())
                .param("date",date.toString()));
    }

    @Test void ownerAndCsrfAreRequired() throws Exception {
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(put(endpoint+"/"+DATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(draft(List.of()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(endpoint+"/"+DATE).with(user(owner())).contentType(MediaType.APPLICATION_JSON).content(json(draft(List.of()))))
                .andExpect(status().isForbidden());
        mvc.perform(delete(endpoint+"/"+DATE).with(user(owner())).param("version","1")).andExpect(status().isForbidden());
    }
    @Test void anotherBusinessCannotReadSaveOrDeleteExceptions() throws Exception {
        String foreign="/api/admin/professionals/"+OTHER_PROFESSIONAL+"/exceptions";
        mvc.perform(get(foreign).with(user(owner()))).andExpect(status().isNotFound());
        mvc.perform(put(foreign+"/"+DATE).with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new SaveException(0,"Folga",List.of())))).andExpect(status().isNotFound());
        mvc.perform(delete(foreign+"/"+DATE).with(user(owner())).with(csrf()).param("version","0")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from date_hours",Integer.class)).isZero();
    }
    @Test void closingOneDateRejectsBookingAndPreservesTheWeeklySchedule() throws Exception {
        save(DATE,draft(List.of())).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        available(DATE).andExpect(jsonPath("$.slots",hasSize(0)));
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(booking(DATE,"09:00"))))
                .andExpect(status().isBadRequest());
        available(DATE.plusDays(1)).andExpect(jsonPath("$.slots",not(empty())));
        assertThat(people.find(BUSINESS,person).hours()).hasSize(12);
    }
    @Test void dateHoursReplaceInsteadOfAddingToWeeklyWindows() throws Exception {
        save(DATE,draft(List.of(window("14:00","16:00")))).andExpect(status().isOk());
        var result=available(DATE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("Motivo privado","customerName","customerPhone");
        assertThat(schedule.availability(catalog.business(BUSINESS),person,SERVICE,DATE).slots())
                .allSatisfy(slot->assertThat(slot.startsAt().atZone(ZONE).getHour()).isBetween(14,15));
        assertThatThrownBy(()->schedule.book(catalog.business(BUSINESS),booking(DATE,"09:00"))).isInstanceOf(BusinessException.class);
        schedule.book(catalog.business(BUSINESS),booking(DATE,"14:00"));
    }
    @Test void canOpenSundayAndBookOutsideTheUsualWeek() {
        LocalDate sunday=DATE.plusDays(5);
        dates.save(BUSINESS,person,sunday,draft(List.of(window("19:00","20:00"))));
        assertThat(schedule.availability(catalog.business(BUSINESS),person,SERVICE,sunday).slots()).isNotEmpty();
        assertThat(schedule.book(catalog.business(BUSINESS),booking(sunday,"19:00")).created()).isTrue();
    }
    @Test void calendarTrimsReasonAndMergesAdjacentIntervals() throws Exception {
        save(DATE,new SaveException(version(),"  Atendimento especial  ",List.of(window("10:00","12:00"),window("09:00","10:00"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstDate").value("2030-06-03"))
                .andExpect(jsonPath("$.exceptions[0].reason").value("Atendimento especial"))
                .andExpect(jsonPath("$.exceptions[0].hours",hasSize(1)));
        assertThat(schedule.book(catalog.business(BUSINESS),booking(DATE,"09:45")).created()).isTrue();
    }
    @Test void existingBlocksRemainEffectiveInsideSpecialHours() {
        dates.save(BUSINESS,person,DATE,draft(List.of(window("09:00","12:00"))));
        schedule.block(catalog.business(BUSINESS),new BlockRequest(person,at("10:00"),at("11:00"),"Compromisso"));
        assertThatThrownBy(()->schedule.book(catalog.business(BUSINESS),booking(DATE,"10:00"))).isInstanceOf(BusinessException.class);
        assertThat(schedule.availability(catalog.business(BUSINESS),person,SERVICE,DATE).slots()).noneSatisfy(slot ->
                assertThat(slot.startsAt()).isEqualTo(at("10:00").toInstant()));
    }
    @Test void confirmedBookingPreventsClosingOrShrinkingWithoutPartialChanges() throws Exception {
        schedule.book(catalog.business(BUSINESS),booking(DATE,"09:00"));
        int before=version();
        save(DATE,draft(List.of())).andExpect(status().isConflict()).andExpect(jsonPath("$.detail",containsString("ficaria fora")));
        save(DATE,draft(List.of(window("09:00","09:30")))).andExpect(status().isConflict());
        assertThat(version()).isEqualTo(before);
        assertThat(dates.list(BUSINESS,person).exceptions()).isEmpty();
    }
    @Test void ongoingAppointmentAlsoPreventsClosingToday() throws Exception {
        schedule.book(catalog.business(BUSINESS),booking(DATE,"09:00"));
        jdbc.update("update appointment set starts_at=?, ends_at=? where professional_id=?",
                OffsetDateTime.parse("2030-06-03T09:45:00Z"),OffsetDateTime.parse("2030-06-03T10:30:00Z"),person);
        save(DATE.minusDays(1),draft(List.of())).andExpect(status().isConflict());
    }
    @Test void cancelledBookingDoesNotPreventClosing() throws Exception {
        schedule.book(catalog.business(BUSINESS),booking(DATE,"09:00"));
        UUID id=jdbc.queryForObject("select id from appointment where professional_id=?",UUID.class,person);
        schedule.cancel(catalog.business(BUSINESS),id);
        save(DATE,draft(List.of())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select status from appointment where id=?",String.class,id)).isEqualTo("CANCELLED");
    }
    @Test void removingSpecialOpeningCannotStrandAConfirmedBooking() throws Exception {
        dates.save(BUSINESS,person,DATE,draft(List.of(window("19:00","20:00"))));
        schedule.book(catalog.business(BUSINESS),booking(DATE,"19:00"));
        mvc.perform(delete(endpoint+"/"+DATE).with(user(owner())).with(csrf()).param("version",Integer.toString(version())))
                .andExpect(status().isConflict());
        assertThat(dates.list(BUSINESS,person).exceptions()).hasSize(1);
    }
    @Test void removingAnExceptionRestoresWeeklyAvailability() throws Exception {
        dates.save(BUSINESS,person,DATE,draft(List.of()));
        int before=version();
        mvc.perform(delete(endpoint+"/"+DATE).with(user(owner())).with(csrf()).param("version",Integer.toString(before)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(before+1)).andExpect(jsonPath("$.exceptions",hasSize(0)));
        available(DATE).andExpect(jsonPath("$.slots",not(empty())));
    }
    @Test void weeklyEditPreservesBookingsSupportedByAnException() {
        dates.save(BUSINESS,person,DATE,draft(List.of(window("19:00","20:00"))));
        schedule.book(catalog.business(BUSINESS),booking(DATE,"19:00"));
        var current=people.find(BUSINESS,person);
        team.update(BUSINESS,person,new UpdateProfessional(current.name(),current.specialty(),true,current.version(),List.of()));
        assertThat(people.find(BUSINESS,person).hours()).isEmpty();
        assertThatThrownBy(()->dates.remove(BUSINESS,person,DATE,version())).isInstanceOf(BusinessException.class);
    }
    @Test void staleVersionsCannotOverwriteWeeklyOrDateEdits() throws Exception {
        int previous=version();
        dates.save(BUSINESS,person,DATE,draft(List.of()));
        save(DATE.plusDays(1),new SaveException(previous,"Outro rascunho",List.of())).andExpect(status().isConflict());
        var profile=people.find(BUSINESS,person);
        assertThatThrownBy(()->team.update(BUSINESS,person,new UpdateProfessional(profile.name(),profile.specialty(),true,previous,profile.hours())))
                .isInstanceOf(BusinessException.class);
    }
    @Test void invalidWindowsDatesAndMissingDataAreRejectedAtomically() throws Exception {
        var invalid=List.of(List.of(window("12:00","09:00")),List.of(window("09:05","12:00")),
                List.of(window("09:00","12:00"),window("11:00","13:00")),
                List.of(window("09:00:01","12:00")),Collections.nCopies(5,window("09:00","10:00")));
        for(var hours:invalid) save(DATE,draft(hours)).andExpect(status().isBadRequest());
        save(DATE,new SaveException(version()," ",List.of())).andExpect(status().isBadRequest());
        save(DATE,new SaveException(null,"Folga",List.of())).andExpect(status().isBadRequest());
        save(DATE,new SaveException(version(),"Folga",null)).andExpect(status().isBadRequest());
        save(DATE,new SaveException(version(),"Folga",Arrays.asList((DayWindow)null))).andExpect(status().isBadRequest());
        save(DATE.minusDays(2),draft(List.of())).andExpect(status().isBadRequest());
        save(DATE.plusDays(366),draft(List.of())).andExpect(status().isBadRequest());
        assertThat(dates.list(BUSINESS,person).exceptions()).isEmpty();
        assertThat(version()).isEqualTo(1);
    }
    @Test void databaseRejectsCrossBusinessDateAndWindowRelationships() {
        dates.save(BUSINESS,person,DATE,draft(List.of()));
        UUID exception=jdbc.queryForObject("select id from date_hours where professional_id=?",UUID.class,person);
        assertThatThrownBy(()->jdbc.update("insert into date_hours_window(id,business_id,exception_id,opens_at,closes_at) values (?,?,?,?,?)",
                UUID.randomUUID(),OTHER_BUSINESS,exception,LocalTime.of(9,0),LocalTime.NOON)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("insert into date_hours(id,business_id,professional_id,exception_date,reason) values (?,?,?,?,?)",
                UUID.randomUUID(),OTHER_BUSINESS,person,DATE,"Inválido")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void inactiveProfessionalRemainsUnavailableDespiteSpecialOpening() throws Exception {
        dates.save(BUSINESS,person,DATE,draft(List.of(window("09:00","12:00"))));
        var profile=people.find(BUSINESS,person);
        team.update(BUSINESS,person,new UpdateProfessional(profile.name(),profile.specialty(),false,profile.version(),profile.hours()));
        available(DATE).andExpect(status().isNotFound());
    }

    @Test void simultaneousBookingAndClosingDateCannotBothWin() throws Exception {
        var request=booking(DATE,"09:00");
        var closing=draft(List.of());
        var outcomes=race(index-> {
            try {
                if(index==0) schedule.book(catalog.business(BUSINESS),request);
                else dates.save(BUSINESS,person,DATE,closing);
                return "saved";
            } catch(BusinessException ex) { assertThat(ex.status().value()).isIn(400,409); return "rejected"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("saved","rejected");
        int bookings=jdbc.queryForObject("select count(*) from appointment where professional_id=?",Integer.class,person);
        assertThat(dates.list(BUSINESS,person).exceptions()).hasSize(bookings==1?0:1);
    }
    @Test void simultaneousEditorsUsingSameVersionCannotOverwriteEachOther() throws Exception {
        var request=draft(List.of());
        var outcomes=race(index-> {
            try { dates.save(BUSINESS,person,DATE.plusDays(index),request); return "saved"; }
            catch(BusinessException ex) { assertThat(ex.status().value()).isEqualTo(409); return "rejected"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("saved","rejected");
        assertThat(dates.list(BUSINESS,person).exceptions()).hasSize(1);
        assertThat(version()).isEqualTo(2);
    }
    @Test void simultaneousRemovalAndBookingOfSpecialOpeningCannotBothWin() throws Exception {
        dates.save(BUSINESS,person,DATE,draft(List.of(window("19:00","20:00"))));
        int expected=version();
        var request=booking(DATE,"19:00");
        var outcomes=race(index-> {
            try {
                if(index==0) schedule.book(catalog.business(BUSINESS),request);
                else dates.remove(BUSINESS,person,DATE,expected);
                return "saved";
            } catch(BusinessException ex) { assertThat(ex.status().value()).isIn(400,409); return "rejected"; }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("saved","rejected");
        int bookings=jdbc.queryForObject("select count(*) from appointment where professional_id=?",Integer.class,person);
        assertThat(dates.list(BUSINESS,person).exceptions()).hasSize(bookings);
    }
    @Test void reschedulingUsesDateExceptionsAndPreservesOriginalOnFailure() {
        schedule.book(catalog.business(BUSINESS),booking(DATE,"09:00"));
        UUID id=jdbc.queryForObject("select id from appointment where professional_id=?",UUID.class,person);
        LocalDate next=DATE.plusDays(1);
        dates.save(BUSINESS,person,next,draft(List.of()));
        var closed=new RescheduleRequest(next.atTime(9,0).atZone(ZONE).toOffsetDateTime());
        assertThatThrownBy(()->schedule.reschedule(catalog.business(BUSINESS),id,closed)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select starts_at from appointment where id=?",OffsetDateTime.class,id).toInstant())
                .isEqualTo(at("09:00").toInstant());
        dates.save(BUSINESS,person,next,draft(List.of(window("19:00","20:00"))));
        var special=new RescheduleRequest(next.atTime(19,0).atZone(ZONE).toOffsetDateTime());
        assertThat(schedule.reschedule(catalog.business(BUSINESS),id,special).startsAt()).isEqualTo(special.startsAt().toInstant());
    }

    private List<String> race(java.util.function.IntFunction<String> action) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->{ready.countDown(); start.await(10,TimeUnit.SECONDS); return action.apply(0);});
            var second=workers.submit(()->{ready.countDown(); start.await(10,TimeUnit.SECONDS); return action.apply(1);});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
        } finally { start.countDown(); }
    }
}
