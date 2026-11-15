package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.luizcode.agenda.identity.*;
import br.com.luizcode.agenda.scheduling.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.mock.web.MockHttpSession;

class ProfessionalAccessTest extends ApiTestBase {
 @Autowired ProfessionalAccessService access;
 @Autowired ProfessionalAgendaService mine;
 @Autowired UserDetailsService users;
 @Autowired PlatformTransactionManager transactions;
 @MockitoSpyBean(name="deterministicClock") Clock clock;
 @MockitoSpyBean CatalogRepository catalogSpy;
 static final String EMAIL="ana-professional@example.test",PASSWORD="Senha profissional longa 123!";
 @BeforeEach void prepare(){
  reset(clock,catalogSpy);doReturn(Instant.parse("2030-06-03T10:00:00Z")).when(clock).instant();
  jdbc.update("delete from professional_access");jdbc.update("delete from app_user where professional_id is not null");
  jdbc.update("update professional set active=true where id in (?,?)",PROFESSIONAL,SECOND_PROFESSIONAL);
 }
 @AfterEach void cleanup(){
  reset(clock,catalogSpy);jdbc.update("delete from appointment");jdbc.update("delete from professional_access");
  jdbc.update("delete from account_action where user_id in (select id from app_user where professional_id is not null)");
  jdbc.update("delete from app_user where professional_id is not null");
  jdbc.update("update professional set active=true where id=?",PROFESSIONAL);
 }
 ProfessionalAccessService.Issued invite(){return access.invite(BUSINESS,PROFESSIONAL,new ProfessionalAccessService.Invite(EMAIL,0));}
 AgendaUser activate(){var issued=invite();access.accept(new ProfessionalAccessService.Accept(issued.token(),PASSWORD));return (AgendaUser)users.loadUserByUsername(EMAIL);}
 String accountHeader(AgendaUser user){return user.id().toString();}
 UUID book(){var receipt=schedule.book(catalog.business(BUSINESS),request("09:00")).receipt();return jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference());}
 AppointmentOutcomeService.Change result(){return new AppointmentOutcomeService.Change(AppointmentOutcomeService.Result.COMPLETED,0,UUID.randomUUID(),"");}
 @Test void inviteUsesDigestAndPreviewDoesNotConsumeAndActivationCreatesNoOwner()throws Exception{
  var issued=invite();assertThat(issued.token()).hasSize(43);
  assertThat(jdbc.queryForObject("select invite_digest from professional_access where professional_id=?",String.class,PROFESSIONAL)).doesNotContain(issued.token()).hasSize(64);
  assertThat(access.preview(issued.token()).email()).isEqualTo(EMAIL);access.preview(issued.token());
  mvc.perform(post("/api/auth/professional-invitations/accept").with(csrf()).contentType("application/json").content(json(new ProfessionalAccessService.Accept(issued.token(),PASSWORD)))).andExpect(status().isNoContent()).andExpect(header().string("Cache-Control","no-store"));
  var staff=(AgendaUser)users.loadUserByUsername(EMAIL);assertThat(staff.role()).isEqualTo("PROFESSIONAL");assertThat(staff.professionalId()).isEqualTo(PROFESSIONAL);assertThat(staff.isEnabled()).isTrue();
  assertThat(staff.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_PROFESSIONAL");
  assertThat(jdbc.queryForObject("select email_verified_at from app_user where id=?",OffsetDateTime.class,staff.id())).isNull();
  assertThatThrownBy(()->access.accept(new ProfessionalAccessService.Accept(issued.token(),PASSWORD))).hasMessageContaining("Convite inválido");
 }
 @Test void passwordValidationDoesNotConsumeInvite(){
  var i=invite();assertThatThrownBy(()->access.accept(new ProfessionalAccessService.Accept(i.token(),"curta"))).isInstanceOf(BusinessException.class);
  access.preview(i.token());
 }
 @Test void expiredInviteAtExactBoundaryIsRejected(){
  var i=invite();doReturn(i.expiresAt()).when(clock).instant();assertThatThrownBy(()->access.preview(i.token())).hasMessageContaining("expirado");
  assertThatThrownBy(()->access.accept(new ProfessionalAccessService.Accept(i.token(),PASSWORD))).hasMessageContaining("expirado");
 }
 @Test void rotationRevokesPreviousLinkAndStaleCommandsCannotOverwrite(){
  var i=invite();var next=access.invite(BUSINESS,PROFESSIONAL,new ProfessionalAccessService.Invite(EMAIL,1));
  assertThatThrownBy(()->access.preview(i.token())).hasMessageContaining("Convite inválido");access.preview(next.token());
  assertThatThrownBy(()->access.revoke(BUSINESS,PROFESSIONAL,1)).hasMessageContaining("outra janela");
 }
 @Test void revokeInvalidatesInviteWithoutDeletingProfessionalOrBooking(){
  var id=book();var i=invite();access.revoke(BUSINESS,PROFESSIONAL,1);
  assertThatThrownBy(()->access.preview(i.token())).hasMessageContaining("Convite inválido");
  assertThat(jdbc.queryForObject("select count(*) from appointment where id=?",Integer.class,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("select active from professional where id=?",Boolean.class,PROFESSIONAL)).isTrue();
 }
 @Test void reactivationKeepsSameIdentityAndRevokesOldCredentials(){
  var staff=activate();access.revoke(BUSINESS,PROFESSIONAL,2);
  assertThatThrownBy(()->access.invite(BUSINESS,PROFESSIONAL,new ProfessionalAccessService.Invite("changed@example.test",3))).hasMessageContaining("mesmo e-mail");
  var i=access.invite(BUSINESS,PROFESSIONAL,new ProfessionalAccessService.Invite(EMAIL,3));access.accept(new ProfessionalAccessService.Accept(i.token(),PASSWORD+"2"));
  var next=(AgendaUser)users.loadUserByUsername(EMAIL);assertThat(next.id()).isEqualTo(staff.id());assertThat(next.credentialsVersion()).isGreaterThan(staff.credentialsVersion());assertThat(next.isEnabled()).isTrue();
 }
 @Test void inactiveProfessionalCannotBeInvitedOrUseExistingAccess()throws Exception{
  var staff=activate();jdbc.update("update professional set active=false where id=?",PROFESSIONAL);
  assertThat(users.loadUserByUsername(EMAIL).isEnabled()).isFalse();
  mvc.perform(get("/api/professional/agenda?date="+DATE).with(user(staff)).header("X-Agenda-Account",accountHeader(staff))).andExpect(status().isUnauthorized());
 }
 @Test void existingOwnerEmailCannotBecomeProfessionalAndForeignProfessionalIsNotFound(){
  assertThatThrownBy(()->access.invite(BUSINESS,PROFESSIONAL,new ProfessionalAccessService.Invite(owner().email(),0))).hasMessageContaining("reservado");
  var existing=UUID.randomUUID();jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name) values(?,?,?,'hash','Responsável')",existing,BUSINESS,"existing-owner@example.test");
  try{assertThatThrownBy(()->access.invite(BUSINESS,PROFESSIONAL,new ProfessionalAccessService.Invite("existing-owner@example.test",0))).hasMessageContaining("vincular");}
  finally{jdbc.update("delete from app_user where id=?",existing);}
  assertThatThrownBy(()->access.invite(BUSINESS,OTHER_PROFESSIONAL,new ProfessionalAccessService.Invite(EMAIL,0))).isInstanceOf(BusinessException.class);
  assertThat(jdbc.queryForObject("select role from app_user where id=?",String.class,owner().id())).isEqualTo("OWNER");
 }
 @Test void successfulLoginReturnsRoleAndRevocationInvalidatesRealSession()throws Exception{
  var staff=activate();
  var login=mvc.perform(post("/api/auth/login").with(csrf()).param("username",EMAIL).param("password",PASSWORD)).andExpect(status().isNoContent()).andReturn();
  var session=(MockHttpSession)login.getRequest().getSession(false);
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PROFESSIONAL")).andExpect(jsonPath("$.professionalId").value(PROFESSIONAL.toString()));
  access.revoke(BUSINESS,PROFESSIONAL,2);
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").with(csrf()).param("username",EMAIL).param("password",PASSWORD)).andExpect(status().isUnauthorized());
  assertThatThrownBy(()->mine.day(staff,DATE)).isInstanceOf(BusinessException.class);
 }
 @ParameterizedTest @ValueSource(strings={"/api/admin/agenda?date=2030-06-04","/api/admin/reports?from=2030-06-04&to=2030-06-04","/api/admin/reports/export?from=2030-06-04&to=2030-06-04","/api/admin/professionals","/api/admin/professional-access","/api/admin/business","/api/admin/mail-queue"})
 void professionalCannotUseAdministrativeRoutes(String path)throws Exception{
  var staff=activate();mvc.perform(get(path).with(user(staff))).andExpect(status().isForbidden());
 }
 @Test void ownerAndAnonymousCannotUseProfessionalApiAndHeaderCannotChangeScope()throws Exception{
  var staff=activate();
  mvc.perform(get("/api/professional/agenda?date="+DATE)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/professional/agenda?date="+DATE).with(user(owner())).header("X-Agenda-Account",accountHeader(staff))).andExpect(status().isForbidden());
  mvc.perform(get("/api/professional/agenda?date="+DATE).with(user(staff)).header("X-Agenda-Account",UUID.randomUUID())).andExpect(status().isPreconditionFailed());
  mvc.perform(get("/api/professional/agenda?date="+DATE).with(user(staff))).andExpect(status().isBadRequest());
 }
 @Test void agendaAndHistoryAreMinimalAndLimitedToOwnProfessional()throws Exception{
  var staff=activate();var id=book();
  schedule.book(catalog.business(BUSINESS),new ScheduleModels.BookingRequest(UUID.randomUUID(),SECOND_PROFESSIONAL,SERVICE,at("10:00"),"Cliente do colega","11999991234"));
  var response=mvc.perform(get("/api/professional/agenda?date="+DATE+"&professionalId="+SECOND_PROFESSIONAL+"&businessId="+OTHER_BUSINESS).with(user(staff)).header("X-Agenda-Account",accountHeader(staff)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.appointments.length()").value(1)).andExpect(jsonPath("$.appointments[0].id").value(id.toString())).andReturn().getResponse().getContentAsString();
  assertThat(response).doesNotContain("customerPhone","priceCents","reference","token","Cliente do colega");
  assertThat(mine.history(staff,id,null).events()).hasSize(1);
  UUID other=jdbc.queryForObject("select id from appointment where professional_id=?",UUID.class,SECOND_PROFESSIONAL);
  assertThatThrownBy(()->mine.history(staff,other,null)).isInstanceOf(BusinessException.class);
  assertThatThrownBy(()->mine.change(staff,other,result())).isInstanceOf(BusinessException.class);
 }
 @Test void ownOutcomeUsesSameDeadlinesVersionsIdempotencyAndProfessionalActor()throws Exception{
  var staff=activate();var id=book();var command=result();
  assertThatThrownBy(()->mine.change(staff,id,command)).hasMessageContaining("término");
  doReturn(at("10:00").toInstant()).when(clock).instant();
  assertThat(mine.change(staff,id,command).status()).isEqualTo("COMPLETED");mine.change(staff,id,command);
  assertThat(jdbc.queryForObject("select count(*) from appointment_history where appointment_id=?",Integer.class,id)).isEqualTo(2);
  assertThat(jdbc.queryForObject("select actor_type from appointment_history where appointment_id=? and appointment_version=1",String.class,id)).isEqualTo("PROFESSIONAL");
  assertThat(jdbc.queryForObject("select actor_user_id from appointment_history where appointment_id=? and appointment_version=1",UUID.class,id)).isEqualTo(staff.id());
  assertThatThrownBy(()->mine.change(staff,id,new AppointmentOutcomeService.Change(AppointmentOutcomeService.Result.NO_SHOW,1,UUID.randomUUID(),""))).hasMessageContaining("5 caracteres");
  mine.change(staff,id,new AppointmentOutcomeService.Change(AppointmentOutcomeService.Result.NO_SHOW,1,UUID.randomUUID(),"Classificação corrigida"));
  assertThat(mine.history(staff,id,null).events()).hasSize(3);
  mvc.perform(post("/api/professional/appointments/"+id+"/outcome").with(user(staff)).header("X-Agenda-Account",accountHeader(staff)).contentType("application/json").content(json(command))).andExpect(status().isForbidden());
 }
 @Test void ownerCommandsRequireMatchingAccountAndCsrf()throws Exception{
  mvc.perform(get("/api/admin/professional-access").with(user(owner())).header("X-Agenda-Account",UUID.randomUUID())).andExpect(status().isPreconditionFailed());
  mvc.perform(post("/api/admin/professional-access/"+PROFESSIONAL+"/invite").with(user(owner())).header("X-Agenda-Account",accountHeader(owner())).contentType("application/json").content(json(new ProfessionalAccessService.Invite(EMAIL,0)))).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/professional-access/"+PROFESSIONAL+"/invite").with(user(owner())).with(csrf()).header("X-Agenda-Account",accountHeader(owner())).contentType("application/json").content(json(new ProfessionalAccessService.Invite(EMAIL,0)))).andExpect(status().isOk());
 }
 List<Integer> race(Callable<?> a,Callable<?> b)throws Exception{
  var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
   var tasks=new ArrayList<Future<Integer>>();for(var task:List.of(a,b))tasks.add(pool.submit(()->{start.await();try{task.call();return 200;}catch(BusinessException e){return e.status().value();}}));
   start.countDown();return List.of(tasks.get(0).get(15,TimeUnit.SECONDS),tasks.get(1).get(15,TimeUnit.SECONDS));
  }finally{start.countDown();}
 }
 @Test void concurrentAcceptanceCreatesExactlyOneAccount()throws Exception{
  var i=invite();var request=new ProfessionalAccessService.Accept(i.token(),PASSWORD);
  assertThat(race(()->access.accept(request),()->access.accept(request))).containsExactlyInAnyOrder(200,400);
  assertThat(jdbc.queryForObject("select count(*) from app_user where professional_id=?",Integer.class,PROFESSIONAL)).isEqualTo(1);
 }
 @Test void concurrentInvitesHaveOneWinner()throws Exception{
  var request=new ProfessionalAccessService.Invite(EMAIL,0);
  assertThat(race(()->access.invite(BUSINESS,PROFESSIONAL,request),()->access.invite(BUSINESS,PROFESSIONAL,request))).containsExactlyInAnyOrder(200,409);
 }
 @Test void revocationWinsAgainstOutcomeWaitingForProfessionalMutex()throws Exception{
  var staff=activate();var id=book();doReturn(at("10:00").toInstant()).when(clock).instant();
  var waiting=new CountDownLatch(1);
  doAnswer(i->{if(Thread.currentThread().getName().startsWith("staff-write"))waiting.countDown();return i.callRealMethod();}).when(catalogSpy).lockProfessional(BUSINESS,PROFESSIONAL);
  try(var pool=Executors.newSingleThreadExecutor(r->new Thread(r,"staff-write"))){
   final Future<?>[] work=new Future<?>[1];
   new TransactionTemplate(transactions).executeWithoutResult(s->{
    catalog.lockProfessional(BUSINESS,PROFESSIONAL);
    work[0]=pool.submit(()->mine.change(staff,id,result()));
    try{assertThat(waiting.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}
    access.revoke(BUSINESS,PROFESSIONAL,2);
   });
   assertThatThrownBy(()->work[0].get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
  }
  assertThat(jdbc.queryForObject("select status from appointment where id=?",String.class,id)).isEqualTo("BOOKED");
 }
}
