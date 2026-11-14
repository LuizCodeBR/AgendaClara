package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.customer.*;
import br.com.luizcode.agenda.customer.CustomerPortalService.*;
import br.com.luizcode.agenda.identity.AgendaUser;
import br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class CustomerPortalTest extends ApiTestBase {
    @Autowired CustomerPortalService portal;
    @Autowired CustomerKeys keys;
    @Autowired br.com.luizcode.agenda.mail.MailPayloadCipher cipher;
    private int[] originalService;
    record Booking(BookingRequest request,UUID id,BookingReceipt receipt){}
    @AfterEach void restoreService(){if(originalService!=null)jdbc.update("update service_offering set duration_minutes=?,price_cents=? where id=?",originalService[0],originalService[1],SERVICE);}
    Booking booking(String when){
        var request=request(when);var receipt=schedule.book(catalog.business(BUSINESS),request).receipt();
        return new Booking(request,jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference()),receipt);
    }
    String path(Booking b){return "/api/customer/reservations/"+b.id();}
    String ownerPath(Booking b){return "/api/admin/appointments/"+b.id()+"/customer-access";}
    Cookie open(Booking b) throws Exception {
        var response=mvc.perform(post("/api/customer/session").with(csrf()).contentType("application/json").content(json(new Exchange(b.receipt().customerAccessToken()))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.reservationKey").value(b.id().toString())).andReturn().getResponse();
        String header=response.getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly","SameSite=Strict","Path="+path(b),"Max-Age=3600");
        return new Cookie(CustomerPortalController.COOKIE,header.split(";",2)[0].substring(CustomerPortalController.COOKIE.length()+1));
    }
    ResultActions cancel(Booking b,Cookie cookie,int version) throws Exception {
        return mvc.perform(post(path(b)+"/cancel").cookie(cookie).with(csrf()).contentType("application/json").content(json(new Change(version))));
    }
    ResultActions move(Booking b,Cookie cookie,String when,int version) throws Exception{
        return mvc.perform(patch(path(b)+"/reschedule").cookie(cookie).with(csrf()).contentType("application/json").content(json(new Move(at(when),version))));
    }

    @Test void derivedCustomerKeyMatchesAnIndependentHkdfVectorAndIsPurposeSeparated(){
        byte[] derived=cipher.deriveKey("customer-reservation-access/v1").getEncoded();
        assertThat(java.util.HexFormat.of().formatHex(derived)).isEqualTo("5c99d4bfc1bb3c1cf5826ab550ec17b337010c3f951e316c20e697ad37b4bf26");
        assertThat(keys.token(UUID.fromString("11111111-1111-1111-1111-111111111111"),1)).isEqualTo("hkXgxMDw8P9aMCPTeG_WKaE8H5vWw__FdhECr3zZlMA");
        assertThat(cipher.deriveKey("another-purpose").getEncoded()).isNotEqualTo(derived);
        assertThat(keys.token(UUID.fromString("11111111-1111-1111-1111-111111111111"),2)).isNotEqualTo(keys.token(UUID.fromString("11111111-1111-1111-1111-111111111111"),1));
    }
    @Test void databaseCannotAttachACustomerSessionToAnotherBusiness(){
        var b=booking("09:00");
        assertThatThrownBy(()->jdbc.update("""
                insert into appointment_customer_session(id,appointment_id,business_id,access_generation,secret_digest,created_at,expires_at)
                values(?,?,?,?,?,?,?)
                """,UUID.randomUUID(),b.id(),OTHER_BUSINESS,1,"a".repeat(64),at("08:00"),at("09:00"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void receiptOffersStablePrivateAccessWithoutStoringOrLoggingItsToken(){
        var b=booking("09:00");String token=b.receipt().customerAccessToken();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(schedule.book(catalog.business(BUSINESS),b.request()).receipt().customerAccessToken()).isEqualTo(token);
        assertThat(jdbc.queryForObject("select token_digest from appointment_access where appointment_id=?",String.class,b.id()))
                .isEqualTo(CustomerKeys.digest(token)).isNotEqualTo(token);
        assertThat(b.receipt().toString()).doesNotContain(token);
        assertThat(new Exchange(token).toString()).doesNotContain(token);
    }
    @Test void grantShowsOnlyItsReservationAndDoesNotBecomeAnOwnerSession() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        mvc.perform(get(path(b)).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.reference").value(b.receipt().reference()))
                .andExpect(jsonPath("$.customerPhone").doesNotExist()).andExpect(jsonPath("$.business.id").doesNotExist()).andExpect(jsonPath("$.customerAccessToken").doesNotExist());
        mvc.perform(get("/api/admin/agenda?date="+DATE).cookie(cookie)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select secret_digest from appointment_customer_session",String.class)).isEqualTo(CustomerKeys.digest(cookie.getValue()));
    }
    @Test void publicReferenceAndPrivateLinkAreNotInterchangeableWithAGrantCookie() throws Exception{
        var b=booking("09:00");
        mvc.perform(get(path(b))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/customer/session").with(csrf()).contentType("application/json").content(json(new Exchange(b.receipt().reference())))).andExpect(status().isBadRequest());
        mvc.perform(get(path(b)).cookie(new Cookie(CustomerPortalController.COOKIE,b.receipt().customerAccessToken()))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/customer/session").with(csrf()).contentType("application/json").content(json(new Exchange("A".repeat(43))))).andExpect(status().isUnauthorized());
    }
    @Test void wrongReservationIdCannotReadQueryOrAlterAnotherBooking() throws Exception{
        var a=booking("09:00");var b=booking("10:00");var cookie=open(a);
        mvc.perform(get(path(b)).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get(path(b)+"/availability?date="+DATE).cookie(cookie)).andExpect(status().isUnauthorized());
        cancel(b,cookie,0).andExpect(status().isUnauthorized());
        move(b,cookie,"11:00",0).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from appointment where status='BOOKED'",Integer.class)).isEqualTo(2);
    }
    @Test void eachReservationCanKeepItsOwnCookieForDifferentTabs() throws Exception{
        var a=booking("09:00");var b=booking("10:00");var ca=open(a);var cb=open(b);
        mvc.perform(get(path(a)).cookie(ca)).andExpect(status().isOk()).andExpect(jsonPath("$.reference").value(a.receipt().reference()));
        mvc.perform(get(path(b)).cookie(cb)).andExpect(status().isOk()).andExpect(jsonPath("$.reference").value(b.receipt().reference()));
        mvc.perform(delete(path(a)+"/session").cookie(ca).with(csrf())).andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie",containsString("Max-Age=0")));
        mvc.perform(get(path(a)).cookie(ca)).andExpect(status().isUnauthorized());
        mvc.perform(get(path(b)).cookie(cb)).andExpect(status().isOk());
    }
    @Test void csrfAndValidVersionsAreRequiredForCustomerMutations() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        mvc.perform(post("/api/customer/session").contentType("application/json").content(json(new Exchange(b.receipt().customerAccessToken())))).andExpect(status().isForbidden());
        mvc.perform(post(path(b)+"/cancel").cookie(cookie).contentType("application/json").content(json(new Change(0)))).andExpect(status().isForbidden());
        mvc.perform(post(path(b)+"/cancel").cookie(cookie).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch(path(b)+"/reschedule").cookie(cookie).with(csrf()).contentType("application/json").content("{\"version\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(delete(path(b)+"/session").cookie(cookie)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select status from appointment where id=?",String.class,b.id())).isEqualTo("BOOKED");
    }
    @Test void cancellationIsIdempotentAndReleasesTheSlot() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        cancel(b,cookie,0).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.version").value(1));
        cancel(b,cookie,0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.canChange").value(false));
        assertThat(schedule.availability(catalog.business(BUSINESS),PROFESSIONAL,SERVICE,DATE).slots()).anyMatch(slot->slot.startsAt().equals(at("09:00").toInstant()));
    }
    @Test void rescheduleExcludesItselfAndKeepsOriginalDurationPriceAndProfessional() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        originalService=jdbc.queryForObject("select duration_minutes,price_cents from service_offering where id=?",(rs,row)->new int[]{rs.getInt(1),rs.getInt(2)},SERVICE);
        jdbc.update("update service_offering set duration_minutes=60,price_cents=12345 where id=?",SERVICE);
        mvc.perform(get(path(b)+"/availability?date="+DATE).cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[*].startsAt",hasItem(at("09:00").toInstant().toString())))
                .andExpect(jsonPath("$.slots[*].startsAt",hasItem(at("11:15").toInstant().toString())));
        move(b,cookie,"09:30",0).andExpect(status().isOk()).andExpect(jsonPath("$.endsAt").value(at("10:15").toInstant().toString()))
                .andExpect(jsonPath("$.priceCents").value(b.receipt().priceCents())).andExpect(jsonPath("$.professionalName").value(b.receipt().professionalName()));
        assertThat(jdbc.queryForObject("select service_id from appointment where id=?",UUID.class,b.id())).isEqualTo(SERVICE);
    }
    @Test void occupiedTimesAndClosedDaysCannotBeChosenDirectly() throws Exception{
        var a=booking("09:00");booking("10:00");var cookie=open(a);
        move(a,cookie,"10:00",0).andExpect(status().isConflict());
        schedule.block(catalog.business(BUSINESS),new BlockRequest(PROFESSIONAL,at("11:00"),at("12:00"),"Teste"));
        move(a,cookie,"11:00",0).andExpect(status().isConflict());
        move(a,cookie,"12:00",0).andExpect(status().isBadRequest());
        mvc.perform(patch(path(a)+"/reschedule").cookie(cookie).with(csrf()).contentType("application/json")
                .content(json(new Move(at("09:00").plusDays(61),0)))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select starts_at from appointment where id=?",OffsetDateTime.class,a.id()).toInstant()).isEqualTo(a.receipt().startsAt());
    }
    @Test void staleCustomerCannotCancelANewTimeChosenByOwner() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        schedule.reschedule(catalog.business(BUSINESS),b.id(),new RescheduleRequest(at("10:00"),0));
        cancel(b,cookie,0).andExpect(status().isConflict());
        mvc.perform(get(path(b)).cookie(cookie)).andExpect(jsonPath("$.startsAt").value(at("10:00").toInstant().toString())).andExpect(jsonPath("$.version").value(1));
        cancel(b,cookie,1).andExpect(status().isOk());
    }
    @Test void duplicateMoveReturnsItsResultAndCannotOverwriteALaterChange() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        move(b,cookie,"10:00",0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        move(b,cookie,"10:00",0).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        schedule.reschedule(catalog.business(BUSINESS),b.id(),new RescheduleRequest(at("11:00"),1));
        move(b,cookie,"10:00",0).andExpect(status().isConflict());
    }
    @Test void hiddenBusinessStillAllowsManagingAnExistingReservation() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        try{jdbc.update("update business set public_booking_enabled=false where id=?",BUSINESS);
            mvc.perform(get("/api/public/"+SLUG+"/catalog")).andExpect(status().isNotFound());
            mvc.perform(get(path(b)).cookie(cookie)).andExpect(status().isOk());
            move(b,cookie,"10:00",0).andExpect(status().isOk());
        }finally{jdbc.update("update business set public_booking_enabled=true where id=?",BUSINESS);}
    }
    @Test void expiredGrantCanBeReopenedWithThePrivateLink() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        jdbc.update("update appointment_customer_session set expires_at=?",OffsetDateTime.parse("2030-06-03T10:00:00Z"));
        mvc.perform(get(path(b)).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get(path(b)).cookie(open(b))).andExpect(status().isOk());
    }
    @Test void expiredLinkAndPastOrCancelledAppointmentsCannotBeChanged() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        jdbc.update("update appointment set starts_at=?,ends_at=? where id=?",at("09:00").minusDays(1).minusHours(3),at("09:00").minusDays(1).minusHours(1),b.id());
        mvc.perform(get(path(b)).cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.canChange").value(false));
        cancel(b,cookie,0).andExpect(status().isConflict());
        jdbc.update("update appointment set starts_at=?,ends_at=? where id=?",at("09:00").minusDays(10),at("09:45").minusDays(10),b.id());
        mvc.perform(get(path(b)).cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/customer/session").with(csrf()).contentType("application/json").content(json(new Exchange(b.receipt().customerAccessToken())))).andExpect(status().isUnauthorized());
    }
    @Test void newOwnerLinkRevokesOldGrantsAndPublicReplayCannotRecoverRotatedAccess() throws Exception{
        var b=booking("09:00");var old=open(b);UUID issueKey=UUID.randomUUID();
        var issued=portal.issue(BUSINESS,b.id(),new Issue(issueKey,0));
        assertThat(issued.token()).isNotEqualTo(b.receipt().customerAccessToken());
        assertThat(portal.issue(BUSINESS,b.id(),new Issue(issueKey,0)).token()).isEqualTo(issued.token());
        mvc.perform(get(path(b)).cookie(old)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/customer/session").with(csrf()).contentType("application/json").content(json(new Exchange(b.receipt().customerAccessToken())))).andExpect(status().isUnauthorized());
        assertThat(schedule.book(catalog.business(BUSINESS),b.request()).receipt().customerAccessToken()).isNull();
        portal.revoke(BUSINESS,b.id(),new Change(1));
        mvc.perform(post("/api/customer/session").with(csrf()).contentType("application/json").content(json(new Exchange(issued.token())))).andExpect(status().isUnauthorized());
    }
    @Test void legacyReservationCanReceiveItsFirstPrivateLinkFromItsOwner() throws Exception{
        var b=booking("09:00");jdbc.update("delete from appointment_access where appointment_id=?",b.id());
        mvc.perform(get(ownerPath(b)).with(user(owner()))).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.token").doesNotExist());
        mvc.perform(post(ownerPath(b)).with(user(owner())).with(csrf()).contentType("application/json").content(json(new Issue(UUID.randomUUID(),0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token",matchesPattern("[A-Za-z0-9_-]{43}"))).andExpect(jsonPath("$.version").value(1));
    }
    @Test void ownerAccessEndpointsRequireTheirBusinessAndCsrf() throws Exception{
        var b=booking("09:00");var other=new AgendaUser(id("estudio-aurora-owner"),OTHER_BUSINESS,"Rafael","rafael@demo.agendaclara.dev","unused");
        mvc.perform(get(ownerPath(b))).andExpect(status().isUnauthorized());
        mvc.perform(get(ownerPath(b)).with(user(other))).andExpect(status().isNotFound());
        mvc.perform(post(ownerPath(b)).with(user(owner())).contentType("application/json").content(json(new Issue(UUID.randomUUID(),0)))).andExpect(status().isForbidden());
        mvc.perform(delete(ownerPath(b)).with(user(other)).with(csrf()).contentType("application/json").content(json(new Change(0)))).andExpect(status().isNotFound());
    }
    @Test void sessionsAreBoundedAndOldestGrantIsRetired() throws Exception{
        var b=booking("09:00");var first=open(b);Cookie last=first;for(int i=0;i<8;i++)last=open(b);
        assertThat(jdbc.queryForObject("select count(*) from appointment_customer_session",Integer.class)).isEqualTo(8);
        mvc.perform(get(path(b)).cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(get(path(b)).cookie(last)).andExpect(status().isOk());
    }
    @Test void getRequestsNeverCancelRescheduleOrCreateSessions() throws Exception{
        var b=booking("09:00");var cookie=open(b);
        mvc.perform(get("/api/customer/session?token="+b.receipt().customerAccessToken())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(path(b)+"/cancel").cookie(cookie)).andExpect(status().isMethodNotAllowed());
        mvc.perform(get(path(b)+"/reschedule").cookie(cookie)).andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject("select status from appointment where id=?",String.class,b.id())).isEqualTo("BOOKED");
        assertThat(jdbc.queryForObject("select count(*) from appointment_customer_session",Integer.class)).isEqualTo(1);
    }
}
