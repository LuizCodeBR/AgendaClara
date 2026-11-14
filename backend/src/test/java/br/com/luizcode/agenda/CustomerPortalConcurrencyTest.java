package br.com.luizcode.agenda;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.luizcode.agenda.customer.*;
import br.com.luizcode.agenda.customer.CustomerPortalService.*;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CustomerPortalConcurrencyTest extends ApiTestBase {
    @Autowired CustomerPortalService portal;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean CustomerAccessRepository access;
    @AfterEach void clearSpy(){reset(access);}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void authorizationIsRecheckedAfterWaitingForOwnerRevocationOrRotation(boolean rotate) throws Exception{
        var receipt=schedule.book(catalog.business(BUSINESS),request("09:00")).receipt();
        UUID id=jdbc.queryForObject("select id from appointment where reference=?",UUID.class,receipt.reference());
        String session=portal.open(receipt.customerAccessToken(),null).secret();
        var read=new CountDownLatch(1);var calls=new AtomicInteger();
        doAnswer(invocation->{var result=invocation.callRealMethod();if(Thread.currentThread().getName().equals("customer-race")&&calls.incrementAndGet()==1)read.countDown();return result;})
                .when(access).grant(anyString(),any(Instant.class));
        var workers=Executors.newSingleThreadExecutor(r->new Thread(r,"customer-race"));
        try{
            final java.util.concurrent.atomic.AtomicReference<Future<Integer>> future=new java.util.concurrent.atomic.AtomicReference<>();
            new TransactionTemplate(transactions).execute(status->{
                catalog.lockProfessional(BUSINESS,PROFESSIONAL);
                future.set(workers.submit(()->{try{
                    // Even guessing the post-revocation appointment version must not bypass authorization.
                    portal.move(session,id,new Move(at("10:00"),1));return 200;
                }catch(BusinessException ex){return ex.status().value();}}));
                try{assertThat(read.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}
                if(rotate)portal.issue(BUSINESS,id,new Issue(UUID.randomUUID(),0));else portal.revoke(BUSINESS,id,new Change(0));
                return true;
            });
            assertThat(future.get().get(15,TimeUnit.SECONDS)).isEqualTo(401);
            assertThat(calls.get()).isGreaterThanOrEqualTo(2);
            assertThat(jdbc.queryForObject("select starts_at from appointment where id=?",OffsetDateTime.class,id).toInstant()).isEqualTo(at("09:00").toInstant());
        }finally{workers.shutdownNow();assertThat(workers.awaitTermination(15,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void simultaneousOpensKeepTheSessionLimit() throws Exception{
        var receipt=schedule.book(catalog.business(BUSINESS),request("09:00")).receipt();
        var ready=new CountDownLatch(10);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(10)){
            var futures=new ArrayList<Future<Boolean>>();
            for(int i=0;i<10;i++)futures.add(pool.submit(()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError();return portal.open(receipt.customerAccessToken(),null).view().reference().equals(receipt.reference());}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            for(var future:futures)assertThat(future.get(20,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("select count(*) from appointment_customer_session",Integer.class)).isEqualTo(8);
        }finally{start.countDown();}
    }
}
