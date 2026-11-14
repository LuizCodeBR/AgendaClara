package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class BookingPolicyMigrationTest {
    @Test void upgradingEightPreservesHistoryAndDefaultsWithoutRetroactiveRestrictions() throws Exception {
        String configured=System.getenv("TEST_DB_URL");
        boolean pg=configured!=null&&configured.startsWith("jdbc:postgresql:");
        String url=pg?configured:"jdbc:h2:mem:customer-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        String user=pg?System.getenv().getOrDefault("TEST_DB_USER","agenda"):"sa";
        String password=pg?System.getenv().getOrDefault("TEST_DB_PASSWORD",""):"";
        String schema="policy_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().dataSource(url,user,password).target("8");
        var next=Flyway.configure().dataSource(url,user,password).target("9");
        if(pg){old.schemas(schema).defaultSchema(schema);next.schemas(schema).defaultSchema(schema);}
        old.load().migrate();
        String scoped=pg?url+(url.contains("?")?"&":"?")+"currentSchema="+schema:url;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(scoped,user,password));
        try {
            UUID b=UUID.randomUUID(),p=UUID.randomUUID(),s=UUID.randomUUID(),a=UUID.randomUUID(),key=UUID.randomUUID();
            var now=OffsetDateTime.parse("2030-06-04T12:00:00Z");
            jdbc.update("insert into business(id,slug,name,category,zone_id,address) values (?,?,?,'Teste','America/Sao_Paulo','Online')",b,b.toString(),"Empresa existente");
            jdbc.update("insert into professional(id,business_id,name,specialty) values (?,?,?,'Teste')",p,b,"Pessoa existente");
            jdbc.update("insert into service_offering(id,business_id,name,description,duration_minutes,price_cents) values (?,?,?,'Teste',45,5500)",s,b,"Serviço existente");
            jdbc.update("""
                insert into appointment(id,business_id,professional_id,service_id,service_name,price_cents,customer_name,customer_phone,
                starts_at,ends_at,status,reference,request_key,request_fingerprint,created_at,updated_at)
                values (?,?,?,?,?,5500,'Cliente antigo','11999991234',?,?,'BOOKED','CUSTOMERMIG',?,?,?,?)
                """,a,b,p,s,"Nome histórico",now,now.plusMinutes(45),key,"b".repeat(64),now.minusDays(1),now.minusDays(1));
            jdbc.update("insert into appointment_access(appointment_id,business_id,generation,token_digest,issued_at) values(?,?,3,?,?)",a,b,"a".repeat(64),now);
            jdbc.update("insert into appointment_customer_session(id,appointment_id,business_id,access_generation,secret_digest,created_at,expires_at) values(?,?,?,3,?,?,?)",UUID.randomUUID(),a,b,"b".repeat(64),now,now.plusHours(1));
            next.load().migrate();
            assertThat(jdbc.queryForObject("select version from appointment where id=?",Integer.class,a)).isZero();
            assertThat(jdbc.queryForObject("select service_name from appointment where id=?",String.class,a)).isEqualTo("Nome histórico");
            assertThat(jdbc.queryForObject("select request_key from appointment where id=?",UUID.class,a)).isEqualTo(key);
            assertThat(jdbc.queryForObject("select ends_at from appointment where id=?",OffsetDateTime.class,a).toInstant()).isEqualTo(now.plusMinutes(45).toInstant());
            assertThat(jdbc.queryForObject("select count(*) from appointment_access",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointment_customer_session",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select booking_policy_version from business where id=?",Integer.class,b)).isZero();
            assertThat(jdbc.queryForObject("select cancel_notice_minutes from appointment where id=?",Integer.class,a)).isZero();
            assertThat(jdbc.queryForObject("select token_digest from appointment_access where appointment_id=?",String.class,a)).isEqualTo("a".repeat(64));
            jdbc.update("update business set cancel_notice_minutes=1440,booking_policy_version=1 where id=?",b);
            next.load().migrate();
            assertThat(jdbc.queryForObject("select cancel_notice_minutes from appointment where id=?",Integer.class,a)).isZero();
            try(var c=jdbc.getDataSource().getConnection()){assertThat(br.com.luizcode.agenda.backup.DatabaseSnapshot.inspect(c,null).schema()).isEqualTo("9");}
        } finally {if(pg)next.cleanDisabled(false).load().clean();else jdbc.execute("shutdown");}
    }
}
