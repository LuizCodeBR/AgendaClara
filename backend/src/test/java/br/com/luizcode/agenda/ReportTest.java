package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.reports.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ReportTest extends ApiTestBase {
    @Autowired ReportService reports;
    @Autowired br.com.luizcode.agenda.scheduling.AppointmentOutcomeService outcomes;
    @MockitoSpyBean br.com.luizcode.agenda.catalog.CatalogRepository catalogSpy;
    @MockitoSpyBean(name="deterministicClock") Clock testClock;
    @AfterEach void restore(){reset(catalogSpy,testClock);}
    UUID book(String time){var result=schedule.book(catalog.business(BUSINESS),request(time)).receipt();return jdbc.queryForObject("select id from appointment where reference=?",UUID.class,result.reference());}
    ReportService.Report report(){return reports.read(BUSINESS,DATE,DATE,null,null);}
    @Test void partitionsStatusesUsingServerTimeAndHistoricalPrices(){
        var completed=book("09:00");var absent=book("10:00");var cancelled=book("11:00");book("14:00");book("15:00");
        jdbc.update("update appointment set status='COMPLETED',price_cents=1234 where id=?",completed);jdbc.update("update appointment set status='NO_SHOW' where id=?",absent);schedule.cancelOwner(catalog.business(BUSINESS),cancelled,0,owner());
        doReturn(at("14:45").toInstant()).when(testClock).instant();
        var r=report();assertThat(r.totals()).isEqualTo(new ReportService.Metrics(5,1,1,1,1,1,1234));
        assertThat(r.days()).hasSize(1);assertThat(r.days().getFirst().metrics()).isEqualTo(r.totals());assertThat(r.professionals()).hasSize(1);assertThat(r.rows()).hasSize(1);
        int price=jdbc.queryForObject("select price_cents from service_offering where id=?",Integer.class,SERVICE);
        jdbc.update("update service_offering set price_cents=999999 where id=?",SERVICE);
        try{assertThat(report().totals().completedValueCents()).isEqualTo(1234);}finally{jdbc.update("update service_offering set price_cents=? where id=?",price,SERVICE);}
    }
    @Test void exactEndIsPendingAndAnInstantBeforeItIsStillOpen(){
        var id=book("09:00");var end=jdbc.queryForObject("select ends_at from appointment where id=?",OffsetDateTime.class,id).toInstant();
        doReturn(end.minusNanos(1000)).when(testClock).instant();assertThat(report().totals().upcoming()).isEqualTo(1);
        doReturn(end).when(testClock).instant();assertThat(report().totals().pending()).isEqualTo(1);assertThat(report().totals().noShow()).isZero();
    }
    @Test void emptyPeriodsIncludeEveryDateAndAcceptExactlyNinetyDays(){
        var r=reports.read(BUSINESS,DATE.minusDays(89),DATE,null,null);assertThat(r.totals().total()).isZero();assertThat(r.days()).hasSize(90);assertThat(r.days()).allMatch(d->d.metrics().total()==0);assertThat(r.rows()).isEmpty();
    }
    @Test void datesUseCompanyZoneIncludingTwentyThreeHourDay(){
        UUID a=book("09:00"),b=book("10:00"),c=book("11:00");
        OffsetDateTime first=OffsetDateTime.parse("2026-03-08T05:00:00Z"),last=OffsetDateTime.parse("2026-03-09T03:30:00Z"),next=OffsetDateTime.parse("2026-03-09T04:00:00Z");
        jdbc.update("update appointment set starts_at=?,ends_at=? where id=?",first,first.plusMinutes(30),a);jdbc.update("update appointment set starts_at=?,ends_at=? where id=?",last,next,b);jdbc.update("update appointment set starts_at=?,ends_at=? where id=?",next,next.plusMinutes(30),c);
        jdbc.update("update business set zone_id='America/New_York' where id=?",BUSINESS);
        try{var r=reports.read(BUSINESS,LocalDate.parse("2026-03-08"),LocalDate.parse("2026-03-08"),null,null);assertThat(r.totals().total()).isEqualTo(2);assertThat(r.days()).hasSize(1);assertThat(r.days().getFirst().metrics().total()).isEqualTo(2);}
        finally{jdbc.update("update business set zone_id=? where id=?",ZONE.getId(),BUSINESS);}
    }
    @Test void filtersAreCombinedAndHistoricalServiceLabelsArePreserved(){
        var a=book("09:00");var b=book("10:00");jdbc.update("update appointment set service_name='Nome antigo' where id=?",a);
        assertThat(reports.read(BUSINESS,DATE,DATE,PROFESSIONAL,SERVICE).rows()).hasSize(2);
        assertThat(reports.read(BUSINESS,DATE,DATE,PROFESSIONAL,SERVICE).rows()).extracting(ReportService.Row::serviceName).contains("Nome antigo");
        assertThat(reports.read(BUSINESS,DATE,DATE,SECOND_PROFESSIONAL,SERVICE).totals().total()).isZero();
        var other=catalog.services(BUSINESS).stream().filter(s->!s.id().equals(SERVICE)).findFirst().orElseThrow();
        assertThat(reports.read(BUSINESS,DATE,DATE,PROFESSIONAL,other.id()).totals().total()).isZero();
    }
    @Test void inactiveProfessionalsRemainInHistoricalReportsAndPrivateOptions(){
        book("09:00");jdbc.update("update professional set active=false where id=?",PROFESSIONAL);
        try{assertThat(reports.read(BUSINESS,DATE,DATE,PROFESSIONAL,null).professionals().getFirst().active()).isFalse();assertThat(reports.options(BUSINESS).professionals()).anyMatch(p->p.id().equals(PROFESSIONAL)&&!p.active());}
        finally{jdbc.update("update professional set active=true where id=?",PROFESSIONAL);}
    }
    @Test void outcomesAndCorrectionsUpdateTotalsWithoutCountingHistoryAsAppointments(){
        var id=book("09:00");doReturn(at("12:00").toInstant()).when(testClock).instant();
        outcomes.change(owner(),id,new br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.Change(br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.Result.NO_SHOW,0,UUID.randomUUID(),"Teste"));assertThat(report().totals().noShow()).isEqualTo(1);
        outcomes.change(owner(),id,new br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.Change(br.com.luizcode.agenda.scheduling.AppointmentOutcomeService.Result.COMPLETED,1,UUID.randomUUID(),"Correção confirmada"));
        var m=report().totals();assertThat(m.total()).isEqualTo(1);assertThat(m.completed()).isEqualTo(1);assertThat(m.noShow()).isZero();assertThat(m.completedValueCents()).isPositive();
    }
    @Test void rescheduleMovesTheReservationToItsCurrentDate(){
        var id=book("09:00");schedule.rescheduleOwner(catalog.business(BUSINESS),id,new br.com.luizcode.agenda.scheduling.ScheduleModels.RescheduleRequest(at("10:00").plusDays(1),0),owner());
        assertThat(report().totals().total()).isZero();assertThat(reports.read(BUSINESS,DATE.plusDays(1),DATE.plusDays(1),null,null).totals().total()).isEqualTo(1);
    }
    @Test void sumsUseLongRatherThanOverflowingIntegerCents(){
        book("09:00");book("10:00");book("11:00");jdbc.update("update appointment set status='COMPLETED',price_cents=2000000000 where business_id=?",BUSINESS);
        assertThat(report().totals().completedValueCents()).isEqualTo(6000000000L);
    }
    @ParameterizedTest @ValueSource(strings={"from=2030-06-05&to=2030-06-04","from=2030-01-01&to=2030-04-01","from=2030-02-30&to=2030-03-01","from=1800-01-01&to=1800-01-02","from=2101-01-01&to=2101-01-02","from=bad&to=2030-06-04"})
    void invalidRangesAreRejectedForBothFormats(String query) throws Exception {
        for(String route:List.of("/api/admin/reports?","/api/admin/reports/export?"))mvc.perform(get(route+query).with(user(owner()))).andExpect(status().isBadRequest());
    }
    @Test void bothFormatsAndOptionsRequireOwnerAndTenantScope() throws Exception {
        String query="?from="+DATE+"&to="+DATE;
        for(String route:List.of("/api/admin/reports","/api/admin/reports/export")){
            mvc.perform(get(route+query)).andExpect(status().isUnauthorized());mvc.perform(get(route+query).with(user("client").roles("CUSTOMER"))).andExpect(status().isForbidden());
            mvc.perform(get(route+query+"&professionalId="+OTHER_PROFESSIONAL).with(user(owner()))).andExpect(status().isNotFound());mvc.perform(get(route+query+"&serviceId="+OTHER_SERVICE).with(user(owner()))).andExpect(status().isNotFound());
            mvc.perform(get(route+query+"&professionalId=bad").with(user(owner()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/reports/options")).andExpect(status().isUnauthorized());
        assertThat(reports.options(BUSINESS).professionals()).noneMatch(p->p.id().equals(OTHER_PROFESSIONAL));assertThat(reports.options(BUSINESS).services()).noneMatch(s->s.id().equals(OTHER_SERVICE));
    }
    @Test void exportsOnlyAggregatesWithNoStoreAttachmentAndSafeCsvCells() throws Exception {
        var id=book("09:00");jdbc.update("update appointment set service_name=? where id=?","=HYPERLINK(\"https://evil.test\");\r\n@SUM(1)",id);
        var response=mvc.perform(get("/api/admin/reports/export?from="+DATE+"&to="+DATE).with(user(owner()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment; filename=\"AgendaClara-relatorio-"))).andReturn().getResponse();
        var csv=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).startsWith("\ufeff\"Tipo\"");assertThat(csv).contains("\"Serviço: =HYPERLINK(\"\"https://evil.test\"\"); @SUM(1)\"");
        assertThat(csv.lines().count()).isEqualTo(3);assertThat(csv).doesNotContain("Cliente Teste","99999","customerAccess","request_key");
        mvc.perform(get("/api/admin/reports?from="+DATE+"&to="+DATE).with(user(owner()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Cliente Teste"))));
    }
    @Test void excessiveGroupingIsRefusedRatherThanTruncated(){
        var id=book("09:00");var rows=new ArrayList<Object[]>();for(int i=0;i<2000;i++){var key=UUID.randomUUID();rows.add(new Object[]{key,"Serviço histórico "+i,key.toString().substring(0,16),UUID.randomUUID(),id});}
        jdbc.batchUpdate("""
            insert into appointment(id,business_id,professional_id,service_id,service_name,price_cents,customer_name,customer_phone,starts_at,ends_at,status,reference,request_key,request_fingerprint,created_at,updated_at)
            select ?,business_id,professional_id,service_id,?,price_cents,customer_name,customer_phone,starts_at,ends_at,status,?,?,request_fingerprint,created_at,updated_at from appointment where id=?
            """,rows);
        assertThatThrownBy(this::report).isInstanceOf(BusinessException.class).hasMessageContaining("2000 combinações");
    }
    @Test void allSectionsObserveOneSnapshotDuringConcurrentCancellation() throws Exception {
        var id=book("09:00");var snapshot=new CountDownLatch(1);var changed=new CountDownLatch(1);
        doAnswer(i->{var result=i.callRealMethod();if(Thread.currentThread().getName().equals("report-reader")){snapshot.countDown();assertThat(changed.await(10,TimeUnit.SECONDS)).isTrue();}return result;}).when(catalogSpy).business(BUSINESS);
        try(var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"report-reader"))){var future=pool.submit(this::report);assertThat(snapshot.await(10,TimeUnit.SECONDS)).isTrue();schedule.cancelOwner(catalog.business(SLUG),id,0,owner());changed.countDown();
            var old=future.get(15,TimeUnit.SECONDS);assertThat(old.totals().cancelled()).isZero();assertThat(old.days().getFirst().metrics()).isEqualTo(old.totals());assertThat(old.rows().getFirst().metrics()).isEqualTo(old.totals());assertThat(report().totals().cancelled()).isEqualTo(1);
        }finally{changed.countDown();}
    }
}