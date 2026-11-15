package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.reports.ReportService;
import br.com.luizcode.agenda.identity.AgendaUser;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ReportReviewTest extends ApiTestBase {
    @Autowired ReportService reports;
    @MockitoSpyBean JdbcTemplate observedJdbc;
    @AfterEach void restoreSpies(){reset(observedJdbc);}
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void newBookingBetweenAggregatesCannotCreatePhantomDailyTotals(boolean existingBooking) throws Exception {
        if(existingBooking)schedule.book(catalog.business(BUSINESS),request("09:00"));
        var aggregated=new CountDownLatch(1);var inserted=new CountDownLatch(1);
        doAnswer(call->{
            var result=call.callRealMethod();
            if(Thread.currentThread().getName().equals("report-review-reader")&&call.<String>getArgument(0).startsWith("select a.professional_id")){
                aggregated.countDown();assertThat(inserted.await(10,TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(observedJdbc).query(anyString(),any(RowMapper.class),any(Object[].class));
        var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"report-review-reader"));
        try {
            var pending=pool.submit(()->reports.read(BUSINESS,DATE,DATE,null,null));
            assertThat(aggregated.await(10,TimeUnit.SECONDS)).isTrue();
            schedule.book(catalog.business(BUSINESS),request("10:00"));inserted.countDown();
            var first=pending.get(15,TimeUnit.SECONDS);
            assertThat(first.totals().total()).isEqualTo(existingBooking?1:0);
            assertThat(first.days().getFirst().metrics()).isEqualTo(first.totals());
            assertThat(reports.read(BUSINESS,DATE,DATE,null,null).totals().total()).isEqualTo(existingBooking?2:1);
        } finally {inserted.countDown();pool.shutdownNow();}
    }

    @Test void timedOutReportDoesNotReturnAnAttachmentOrInternalQueryDetails() throws Exception {
        doThrow(new org.springframework.dao.QueryTimeoutException("private-query-marker")).when(observedJdbc)
            .query(startsWith("select a.professional_id"),any(RowMapper.class),any(Object[].class));
        for(String path:List.of("/api/admin/reports?from="+DATE+"&to="+DATE,"/api/admin/reports/export?from="+DATE+"&to="+DATE))
            mvc.perform(get(path).with(user(owner()))).andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-query-marker"))));
    }


    @Test void companyHeaderNeverGrantsAccessAndUnfilteredReportsStayScopedToTheSession() throws Exception {
        schedule.book(catalog.business(BUSINESS),request("09:00"));schedule.book(catalog.business(BUSINESS),request("10:00"));
        schedule.book(catalog.business(OTHER_BUSINESS),new br.com.luizcode.agenda.scheduling.ScheduleModels.BookingRequest(
            UUID.randomUUID(),OTHER_PROFESSIONAL,OTHER_SERVICE,at("09:00"),"Outro cliente privado","11999991234"));
        var other=new AgendaUser(id("estudio-aurora-owner"),OTHER_BUSINESS,"Rafael","rafael@demo.agendaclara.dev","unused");
        String query="?from="+DATE+"&to="+DATE+"&businessId="+BUSINESS;
        mvc.perform(get("/api/admin/reports"+query).header("X-Agenda-Business",OTHER_BUSINESS).with(user(other)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totals.total").value(1))
            .andExpect(jsonPath("$.rows[0].professionalId").value(OTHER_PROFESSIONAL.toString()));
        mvc.perform(get("/api/admin/reports/export"+query).header("X-Agenda-Business",OTHER_BUSINESS).with(user(other)))
            .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PROFESSIONAL.toString()))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Outro cliente privado"))));
        for(String path:List.of("/api/admin/reports/options","/api/admin/reports"+query,"/api/admin/reports/export"+query)){
            mvc.perform(get(path).header("X-Agenda-Business",BUSINESS)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("X-Agenda-Business",BUSINESS).with(user("customer").roles("CUSTOMER"))).andExpect(status().isForbidden());
            mvc.perform(get(path).header("X-Agenda-Business","invalid").with(user(owner()))).andExpect(status().isBadRequest());
        }
    }

    @Test void aDifferentActiveCompanyCannotSilentlyReplaceTheDisplayedReportOrExport() throws Exception {
        var other=new AgendaUser(id("estudio-aurora-owner"),OTHER_BUSINESS,"Outra responsável","other@demo.agendaclara.dev","unused");
        for(String path:List.of("/api/admin/reports/options","/api/admin/reports?from="+DATE+"&to="+DATE,"/api/admin/reports/export?from="+DATE+"&to="+DATE))
            mvc.perform(get(path).header("X-Agenda-Business",BUSINESS.toString()).with(user(other)))
                .andExpect(status().isPreconditionFailed()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }
}
