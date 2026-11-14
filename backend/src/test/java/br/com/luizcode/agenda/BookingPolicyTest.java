package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.policy.*;
import br.com.luizcode.agenda.customer.*;
import br.com.luizcode.agenda.customer.CustomerPortalService.*;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.shared.BusinessException;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class BookingPolicyTest extends ApiTestBase {
    static final String POLICY="/api/admin/business/booking-policy";
    static final Instant NOW=Instant.parse("2030-06-03T10:00:00Z");
    @Autowired BookingPolicyService policies;
    @Autowired CustomerPortalService portal;
    @MockitoSpyBean(name="deterministicClock") Clock testClock;
    @BeforeEach void policySetup(){
        defaults();doReturn(NOW).when(testClock).instant();
        doAnswer(i->Clock.fixed(testClock.instant(),i.getArgument(0))).when(testClock).withZone(any(ZoneId.class));
    }
    @AfterEach void restore(){defaults();reset(testClock);}
    void defaults(){jdbc.update("update business set booking_notice_minutes=0,cancel_notice_minutes=0,reschedule_notice_minutes=0,booking_policy_version=0");}
    BookingPolicy save(int booking,int cancel,int move){return policies.save(BUSINESS,new BookingPolicyService.Save(booking,cancel,move,policies.get(BUSINESS).version()));}
    BookingRequest withPolicy(String time,int version){var r=request(time);return new BookingRequest(r.requestKey(),r.professionalId(),r.serviceId(),r.startsAt(),r.customerName(),r.customerPhone(),version);}
    record Reserved(UUID id,BookingReceipt receipt){}
    Reserved book(String when){var receipt=schedule.book(catalog.business(BUSINESS),withPolicy(when,policies.get(BUSINESS).version())).receipt();return new Reserved(jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference()),receipt);}
    String path(Reserved r){return "/api/customer/reservations/"+r.id();}
    Cookie open(Reserved r){return new Cookie(CustomerPortalController.COOKIE,portal.open(r.receipt().customerAccessToken(),null).secret());}

    @Test void defaultRulesPreserveExistingBehaviorAndAreVisibleInTheCatalog() throws Exception{
        mvc.perform(get(POLICY).with(user(owner()))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.bookingNoticeMinutes").value(0));
        mvc.perform(get("/api/public/"+SLUG+"/catalog")).andExpect(jsonPath("$.bookingPolicy.cancellationNoticeMinutes").value(0));
        assertThat(book("09:00").receipt().bookingPolicy()).isEqualTo(BookingPolicy.NONE);
    }
    @Test void settingsRequireOwnerCsrfAllFieldsAndValidRanges() throws Exception{
        String valid=json(new BookingPolicyService.Save(120,1440,720,0));
        mvc.perform(get(POLICY)).andExpect(status().isUnauthorized());
        mvc.perform(put(POLICY).with(user(owner())).contentType("application/json").content(valid)).andExpect(status().isForbidden());
        for(String body:List.of("{}","{\"bookingNoticeMinutes\":-1,\"cancellationNoticeMinutes\":0,\"rescheduleNoticeMinutes\":0,\"version\":0}",
            "{\"bookingNoticeMinutes\":0,\"cancellationNoticeMinutes\":10081,\"rescheduleNoticeMinutes\":0,\"version\":0}",
            "{\"bookingNoticeMinutes\":0,\"cancellationNoticeMinutes\":0,\"rescheduleNoticeMinutes\":0,\"version\":-1}"))
            mvc.perform(put(POLICY).with(user(owner())).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertThat(policies.get(BUSINESS)).isEqualTo(BookingPolicy.NONE);
    }
    @Test void settingsAreScopedToTheOwnerBusinessAndUseOptimisticVersion() throws Exception{
        mvc.perform(put(POLICY).with(user(owner())).with(csrf()).contentType("application/json")
            .content("{\"businessId\":\""+OTHER_BUSINESS+"\",\"bookingNoticeMinutes\":120,\"cancellationNoticeMinutes\":1440,\"rescheduleNoticeMinutes\":720,\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(policies.get(OTHER_BUSINESS)).isEqualTo(BookingPolicy.NONE);
        mvc.perform(put(POLICY).with(user(owner())).with(csrf()).contentType("application/json").content(json(new BookingPolicyService.Save(0,0,0,0)))).andExpect(status().isConflict());
        assertThat(policies.get(BUSINESS)).isEqualTo(new BookingPolicy(120,1440,720,1));
    }
    @Test void savingUnchangedRulesDoesNotCreateAnotherRevision(){assertThat(save(0,0,0).version()).isZero();}
    @Test void availabilityAndDirectBookingEnforceTheSameLeadTimeAtTheBoundary(){
        save(1560,0,0); // Tomorrow 09:00 is exactly 26 elapsed hours away.
        var available=schedule.availability(catalog.business(BUSINESS),PROFESSIONAL,SERVICE,DATE);
        assertThat(available.slots()).anyMatch(s->s.startsAt().equals(at("09:00").toInstant()));
        save(1561,0,0);
        available=schedule.availability(catalog.business(BUSINESS),PROFESSIONAL,SERVICE,DATE);
        assertThat(available.slots()).noneMatch(s->s.startsAt().equals(at("09:00").toInstant()));
        assertThatThrownBy(()->book("09:00")).isInstanceOf(BusinessException.class).hasMessageContaining("antecedência");
        assertThat(book("09:15").receipt().bookingPolicy().bookingNoticeMinutes()).isEqualTo(1561);
    }
    @Test void changedRulesRequireTheCustomerToReviewTheCurrentRevision() throws Exception{
        save(0,1440,720);
        mvc.perform(post(BOOKINGS).with(csrf()).contentType("application/json").content(json(withPolicy("09:00",0)))).andExpect(status().isConflict());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType("application/json").content(json(request("09:00")))).andExpect(status().isConflict());
        mvc.perform(post(BOOKINGS).with(csrf()).contentType("application/json").content(json(withPolicy("09:00",1)))).andExpect(status().isCreated()).andExpect(jsonPath("$.bookingPolicy.version").value(1));
    }
    @Test void confirmedRulesAreSnapshotsAndChangesDoNotRestrictOlderReservations() throws Exception{
        var old=book("09:00");save(0,10080,10080);var recent=book("10:00");
        var oldView=portal.read(open(old).getValue(),old.id());var newView=portal.read(open(recent).getValue(),recent.id());
        assertThat(oldView.canCancel()).isTrue();assertThat(oldView.canReschedule()).isTrue();assertThat(oldView.bookingPolicy()).isEqualTo(BookingPolicy.NONE);
        assertThat(newView.canCancel()).isFalse();assertThat(newView.canReschedule()).isFalse();
        save(0,0,0);newView=portal.read(open(recent).getValue(),recent.id());
        assertThat(newView.canCancel()).isFalse();assertThat(newView.bookingPolicy().version()).isEqualTo(1);
    }
    @Test void cancellationAndRescheduleHaveIndependentDeadlinesWithInclusiveBoundary() throws Exception{
        save(0,1560,1561);var r=book("09:00");var cookie=open(r);
        mvc.perform(get(path(r)).cookie(cookie)).andExpect(jsonPath("$.canCancel").value(true)).andExpect(jsonPath("$.canReschedule").value(false)).andExpect(jsonPath("$.cancelUntil").value(NOW.toString()));
        mvc.perform(patch(path(r)+"/reschedule").cookie(cookie).with(csrf()).contentType("application/json").content(json(new Move(at("10:00"),0)))).andExpect(status().isConflict());
        mvc.perform(post(path(r)+"/cancel").cookie(cookie).with(csrf()).contentType("application/json").content(json(new Change(0)))).andExpect(status().isOk());
        mvc.perform(post(path(r)+"/cancel").cookie(cookie).with(csrf()).contentType("application/json").content(json(new Change(0)))).andExpect(status().isOk());
    }
    @Test void elapsedDeadlineIsRecheckedWhenTheCustomerConfirms() throws Exception{
        save(0,1560,0);var r=book("09:00");var cookie=open(r);
        assertThat(portal.read(cookie.getValue(),r.id()).canCancel()).isTrue();
        doReturn(NOW.plusNanos(1)).when(testClock).instant();
        mvc.perform(post(path(r)+"/cancel").cookie(cookie).with(csrf()).contentType("application/json").content(json(new Change(0)))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from appointment where id=?",String.class,r.id())).isEqualTo("BOOKED");
    }
    @Test void reschedulingFiltersAndValidatesTheNewLeadTime(){
        save(60,0,0);var r=book("09:00");
        doReturn(at("08:30").toInstant()).when(testClock).instant();var cookie=open(r);
        var slots=portal.availability(cookie.getValue(),r.id(),DATE).slots();
        assertThat(slots).allMatch(s->!s.startsAt().isBefore(at("09:30").toInstant()));
        assertThatThrownBy(()->portal.move(cookie.getValue(),r.id(),new Move(at("09:15"),0))).hasMessageContaining("antecedência");
        var moved=portal.move(cookie.getValue(),r.id(),new Move(at("10:00"),0));
        assertThat(moved.priceCents()).isEqualTo(r.receipt().priceCents());assertThat(moved.bookingPolicy()).isEqualTo(r.receipt().bookingPolicy());
    }
    @Test void reschedulingRecalculatesDeadlinesButKeepsTheContractAndSafeReplay(){
        save(0,60,1560);var r=book("09:00");var cookie=open(r);
        var moved=portal.move(cookie.getValue(),r.id(),new Move(at("10:00"),0));
        assertThat(moved.cancelUntil()).isEqualTo(at("09:00").toInstant());assertThat(moved.rescheduleUntil()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        doReturn(NOW.plus(Duration.ofHours(1)).plusSeconds(1)).when(testClock).instant();cookie=open(r);
        var replay=portal.move(cookie.getValue(),r.id(),new Move(at("10:00"),0));assertThat(replay.version()).isEqualTo(1);
        String secret=cookie.getValue();
        assertThatThrownBy(()->portal.move(secret,r.id(),new Move(at("11:00"),1))).hasMessageContaining("prazo de remarcação");
    }
    @Test void publicBookingRetryKeepsItsOriginalRulesAfterTheCompanyChangesThem(){
        var request=withPolicy("09:00",0);var first=schedule.book(catalog.business(BUSINESS),request).receipt();save(10080,10080,10080);
        var retry=schedule.book(catalog.business(BUSINESS),request);
        assertThat(retry.created()).isFalse();assertThat(retry.receipt()).isEqualTo(first);
    }
    @Test void responsibleCanHandleFutureExceptionsAfterCustomerDeadlines(){
        save(0,10080,10080);var r=book("09:00");assertThat(portal.read(open(r).getValue(),r.id()).canCancel()).isFalse();
        var moved=schedule.reschedule(catalog.business(BUSINESS),r.id(),new RescheduleRequest(at("10:00"),0));
        assertThat(moved.startsAt()).isEqualTo(at("10:00").toInstant());assertThat(moved.bookingPolicy()).isEqualTo(r.receipt().bookingPolicy());
        assertThat(schedule.cancel(catalog.business(BUSINESS),r.id(),1).status()).isEqualTo("CANCELLED");
    }
    @Test void ownerAvailabilityIsScopedAndDoesNotApplyPublicLeadTime() throws Exception{
        save(0,10080,10080);var r=book("09:00");save(10080,0,0);
        assertThat(schedule.availability(catalog.business(BUSINESS),PROFESSIONAL,SERVICE,DATE).slots()).isEmpty();
        mvc.perform(get("/api/admin/appointments/"+r.id()+"/availability?date="+DATE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/appointments/"+r.id()+"/availability?date="+DATE).with(user(owner())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.slots",org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
        var foreign=new br.com.luizcode.agenda.identity.AgendaUser(id("estudio-aurora-owner"),OTHER_BUSINESS,"Rafael","rafael@demo.agendaclara.dev","unused");
        mvc.perform(get("/api/admin/appointments/"+r.id()+"/availability?date="+DATE).with(user(foreign))).andExpect(status().isNotFound());
    }
    @Test void ownerAvailabilityKeepsOriginalDurationAndExcludesItsOwnReservation(){
        var r=book("09:00");
        int duration=jdbc.queryForObject("select duration_minutes from service_offering where id=?",Integer.class,SERVICE);
        try{
            jdbc.update("update service_offering set duration_minutes=60 where id=?",SERVICE);
            var slots=schedule.adminAvailability(catalog.business(BUSINESS),r.id(),DATE).slots();
            assertThat(slots).anyMatch(s->s.startsAt().equals(at("09:00").toInstant()));
            assertThat(slots).allMatch(s->Duration.between(s.startsAt(),s.endsAt()).toMinutes()==45);
        }finally{jdbc.update("update service_offering set duration_minutes=? where id=?",duration,SERVICE);}
    }
    @Test void publicBookingRechecksPublicationAfterTakingThePolicyLock(){
        var business=catalog.business(BUSINESS);
        jdbc.update("update business set public_booking_enabled=false where id=?",BUSINESS);
        try{assertThatThrownBy(()->schedule.bookPublic(business,request("09:00"))).isInstanceOf(BusinessException.class).hasMessageContaining("Registro não encontrado");}
        finally{jdbc.update("update business set public_booking_enabled=true where id=?",BUSINESS);}
        assertThat(jdbc.queryForObject("select count(*) from appointment",Integer.class)).isZero();
    }
    @Test void databaseRefusesInvalidRulesOutsideTheHttpLayer(){
        assertThatThrownBy(()->jdbc.update("update business set booking_notice_minutes=-1 where id=?",BUSINESS)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(policies.get(BUSINESS)).isEqualTo(BookingPolicy.NONE);
    }
}
