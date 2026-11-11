package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class BookingApiTest extends ApiTestBase {
    @Test void anonymousCannotReadAdmin() throws Exception {
        mvc.perform(get("/api/admin/agenda").param("date", DATE.toString())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
    @Test void mutationsRequireCsrf() throws Exception {
        mvc.perform(post(BOOKINGS).contentType(MediaType.APPLICATION_JSON).content(json(request("09:00"))))
                .andExpect(status().isForbidden());
    }
    @Test void demoLoginUsesRealPasswordAndSession() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "marina@demo.agendaclara.dev")
                .param("password", "wrong")).andExpect(status().isUnauthorized());
        var login = mvc.perform(post("/api/auth/login").with(csrf()).param("username", "marina@demo.agendaclara.dev")
                .param("password", "Demo@12345")).andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.business.id").value(BUSINESS.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void bookingHasServerCalculatedDurationPriceAndSafeReceipt() throws Exception {
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:00"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.priceCents").value(5500))
                .andExpect(jsonPath("$.startsAt").value(at("09:00").toInstant().toString()))
                .andExpect(jsonPath("$.endsAt").value(at("09:45").toInstant().toString()))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.customerPhone").doesNotExist());
    }
    @Test void repeatedIdenticalRequestReturnsSameBooking() throws Exception {
        var request = request("09:00");
        var first = mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var second = mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from appointment", Integer.class)).isEqualTo(1);
    }
    @Test void sameKeyWithDifferentContentConflicts() throws Exception {
        var original = request("09:00");
        schedule.book(catalog.business(BUSINESS), original);
        var changed = new BookingRequest(original.requestKey(), PROFESSIONAL, SERVICE, at("10:00"), "Outra pessoa", "11999991234");
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(changed)))
                .andExpect(status().isConflict());
    }
    @Test void overlappingIntervalsAreRejectedAndAdjacentIntervalsAllowed() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:15"))))
                .andExpect(status().isConflict());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:45"))))
                .andExpect(status().isCreated());
    }
    @Test void differentProfessionalsCanUseSameTime() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        var other = new BookingRequest(UUID.randomUUID(), SECOND_PROFESSIONAL, SERVICE, at("09:00"), "Outro cliente", "11999991234");
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(other)))
                .andExpect(status().isCreated());
    }
    @ParameterizedTest @ValueSource(strings = {"08:45", "09:05", "11:30", "12:00", "17:30", "18:00", "09:00:01"})
    void offGridOrOutsideWorkingWindowsIsRejected(String time) throws Exception {
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request(time))))
                .andExpect(status().isBadRequest());
    }
    @Test void sundayIsClosed() throws Exception {
        var request = new BookingRequest(UUID.randomUUID(), PROFESSIONAL, SERVICE,
                LocalDate.of(2030,6,9).atTime(9,0).atZone(ZONE).toOffsetDateTime(), "Cliente", "11999991234");
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest());
    }
    @Test void pastAndDistantDatesAreRejected() throws Exception {
        for (LocalDate date : new LocalDate[]{LocalDate.of(2030,6,2), LocalDate.of(2030,8,4)}) {
            mvc.perform(get(AVAILABILITY).param("professionalId", PROFESSIONAL.toString())
                    .param("serviceId", SERVICE.toString()).param("date", date.toString()))
                    .andExpect(status().isBadRequest());
        }
    }
    @Test void invalidContactAndNullFieldsAreRejected() throws Exception {
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        var invalid = new BookingRequest(UUID.randomUUID(), PROFESSIONAL, SERVICE, at("09:00"), " ", "abc");
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest());
    }
    @Test void serviceFromAnotherBusinessCannotBeBooked() throws Exception {
        var foreign = new BookingRequest(UUID.randomUUID(), PROFESSIONAL, OTHER_SERVICE, at("09:00"), "Cliente", "11999991234");
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(foreign)))
                .andExpect(status().isNotFound());
        mvc.perform(get(AVAILABILITY).param("professionalId", OTHER_PROFESSIONAL.toString())
                .param("serviceId", SERVICE.toString()).param("date", DATE.toString()))
                .andExpect(status().isNotFound());
    }
    @Test void publicAvailabilityContainsNoCustomerInformation() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        mvc.perform(get(AVAILABILITY).param("professionalId", PROFESSIONAL.toString()).param("serviceId", SERVICE.toString())
                .param("date", DATE.toString())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Cliente Teste"))))
                .andExpect(content().string(not(containsString("99999"))))
                .andExpect(jsonPath("$.slots[*].startsAt", not(hasItem(at("09:00").toInstant().toString()))))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
    @Test void cancelIsRepeatableAndReleasesTime() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        UUID id = jdbc.queryForObject("select id from appointment", UUID.class);
        for (int i=0; i<2; i++) mvc.perform(post("/api/admin/appointments/" + id + "/cancel").with(user(owner())).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:00"))))
                .andExpect(status().isCreated());
    }
    @Test void ownerCannotCancelRescheduleOrBlockAnotherBusiness() throws Exception {
        var other = new BookingRequest(UUID.randomUUID(), OTHER_PROFESSIONAL, OTHER_SERVICE, at("09:00"), "Cliente Aurora", "11999991234");
        schedule.book(catalog.business(OTHER_BUSINESS), other);
        UUID id = jdbc.queryForObject("select id from appointment", UUID.class);
        mvc.perform(post("/api/admin/appointments/" + id + "/cancel").with(user(owner())).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/appointments/" + id + "/reschedule").with(user(owner())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(new RescheduleRequest(at("14:00")))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/blocks").with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new BlockRequest(OTHER_PROFESSIONAL, at("14:00"), at("15:00"), "Reunião"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/agenda").with(user(owner())).param("date", DATE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointments", hasSize(0)));
    }
    @Test void rescheduleIsAtomicOnConflictAndPreservesSnapshot() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        UUID id = jdbc.queryForObject("select id from appointment", UUID.class);
        schedule.book(catalog.business(BUSINESS), request("10:00"));
        mvc.perform(patch("/api/admin/appointments/" + id + "/reschedule").with(user(owner())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(new RescheduleRequest(at("10:15")))))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select starts_at from appointment where id = ?", OffsetDateTime.class, id).toInstant())
                .isEqualTo(at("09:00").toInstant());
        mvc.perform(patch("/api/admin/appointments/" + id + "/reschedule").with(user(owner())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json(new RescheduleRequest(at("14:00")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endsAt").value(at("14:45").toInstant().toString()))
                .andExpect(jsonPath("$.priceCents").value(5500));
    }
    @Test void blockPreventsBookingAndCanBeRemoved() throws Exception {
        mvc.perform(post("/api/admin/blocks").with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new BlockRequest(PROFESSIONAL, at("09:00"), at("10:00"), "Compromisso"))))
                .andExpect(status().isCreated());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:15"))))
                .andExpect(status().isConflict());
        UUID id = jdbc.queryForObject("select id from time_block", UUID.class);
        mvc.perform(delete("/api/admin/blocks/" + id).with(user(owner())).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request("09:15"))))
                .andExpect(status().isCreated());
    }
    @Test void blockCannotOverwriteAppointmentOrHaveReversedInterval() throws Exception {
        schedule.book(catalog.business(BUSINESS), request("09:00"));
        mvc.perform(post("/api/admin/blocks").with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new BlockRequest(PROFESSIONAL, at("09:15"), at("10:00"), "Reunião"))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/blocks").with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(new BlockRequest(PROFESSIONAL, at("14:00"), at("13:00"), "Reunião"))))
                .andExpect(status().isBadRequest());
    }
    @Test void createdServiceIsScopedToAuthenticatedOwner() throws Exception {
        mvc.perform(post("/api/admin/services").with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name","Serviço de teste","description","Descrição","durationMinutes",30,"priceCents",1500,
                        "businessId",OTHER_BUSINESS.toString()))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select business_id from service_offering where name = 'Serviço de teste'", UUID.class)).isEqualTo(BUSINESS);
        jdbc.update("delete from service_offering where name = 'Serviço de teste'");
    }
    @Test void invalidServiceDurationIsRejected() throws Exception {
        mvc.perform(post("/api/admin/services").with(user(owner())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name","Serviço","description","Descrição","durationMinutes",22,"priceCents",1500))))
                .andExpect(status().isBadRequest());
    }
    @Test void retryOfCancelledBookingCannotClaimItIsConfirmed() throws Exception {
        var request = request("09:00");
        schedule.book(catalog.business(BUSINESS), request);
        UUID id = jdbc.queryForObject("select id from appointment", UUID.class);
        schedule.cancel(catalog.business(BUSINESS), id);
        mvc.perform(post(BOOKINGS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from appointment where status = 'BOOKED'", Integer.class)).isZero();
    }}
