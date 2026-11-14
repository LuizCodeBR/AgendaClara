package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ProfessionalServicesMigrationTest {
    @Test void upgradingSixPreservesExistingCombinationsIncludingInactiveProfilesAndHistory() throws Exception {
        String configured=System.getenv("TEST_DB_URL");
        boolean postgres=configured!=null&&configured.startsWith("jdbc:postgresql:");
        String url=postgres?configured:"jdbc:h2:mem:service-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        String user=postgres?System.getenv().getOrDefault("TEST_DB_USER","agenda"):"sa";
        String password=postgres?System.getenv().getOrDefault("TEST_DB_PASSWORD",""):"";
        String schema="service_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var old=Flyway.configure().dataSource(url,user,password).target("6");
        if(postgres)old.schemas(schema).defaultSchema(schema);
        old.load().migrate();
        String scoped=postgres?url+(url.contains("?")?"&":"?")+"currentSchema="+schema:url;
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(scoped,user,password));
        var next=Flyway.configure().dataSource(url,user,password).target("7");
        if(postgres)next.schemas(schema).defaultSchema(schema);
        try {
            UUID a=UUID.randomUUID(),b=UUID.randomUUID(),p=UUID.randomUUID(),inactive=UUID.randomUUID(),other=UUID.randomUUID(),s=UUID.randomUUID(),s2=UUID.randomUUID(),foreign=UUID.randomUUID();
            for(UUID business: new UUID[]{a,b})jdbc.update("insert into business(id,slug,name,category,zone_id,address) values (?,?,?,?,?,?)",business,business.toString(),"Negócio existente","Teste","America/Sao_Paulo","Online fictício");
            jdbc.update("insert into professional(id,business_id,name,specialty,active,version) values (?,?,?,?,true,7)",p,a,"Pessoa existente","Teste");
            jdbc.update("insert into professional(id,business_id,name,specialty,active,version) values (?,?,?,?,false,4)",inactive,a,"Pessoa inativa","Teste");
            jdbc.update("insert into professional(id,business_id,name,specialty) values (?,?,?,?)",other,b,"Outra pessoa","Teste");
            for(UUID id:new UUID[]{s,s2,foreign})jdbc.update("insert into service_offering(id,business_id,name,description,duration_minutes,price_cents) values (?,?,?,?,30,1234)",id,id.equals(foreign)?b:a,"Serviço existente","Teste");
            UUID key=UUID.randomUUID(),booking=UUID.randomUUID();
            OffsetDateTime start=OffsetDateTime.parse("2030-06-04T12:00:00Z");
            jdbc.update("""
                    insert into appointment(id,business_id,professional_id,service_id,service_name,price_cents,customer_name,customer_phone,
                    starts_at,ends_at,status,reference,request_key,request_fingerprint,created_at,updated_at)
                    values (?,?,?,?,?,1234,'Cliente existente','11999991234',?,?,'BOOKED','SERVICEMIG',?,?,?,?)
                    """,booking,a,p,s,"Nome preservado",start,start.plusMinutes(30),key,"a".repeat(64),start.minusDays(1),start.minusDays(1));
            try(var connection=jdbc.getDataSource().getConnection()){assertThat(br.com.luizcode.agenda.backup.DatabaseSnapshot.inspect(connection,null).schema()).isEqualTo("6");}
            next.load().migrate();
            try(var connection=jdbc.getDataSource().getConnection()){assertThat(br.com.luizcode.agenda.backup.DatabaseSnapshot.inspect(connection,null).schema()).isEqualTo("7");}
            assertThat(jdbc.queryForObject("select count(*) from professional_service",Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("select count(*) from professional_service where professional_id=?",Integer.class,inactive)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from professional_service where business_id=? and service_id=?",Integer.class,a,foreign)).isZero();
            assertThat(jdbc.queryForObject("select active from professional where id=?",Boolean.class,inactive)).isFalse();
            assertThat(jdbc.queryForObject("select version from professional where id=?",Integer.class,p)).isEqualTo(7);
            assertThat(jdbc.queryForObject("select request_key from appointment where id=?",UUID.class,booking)).isEqualTo(key);
            assertThat(jdbc.queryForObject("select service_name from appointment where id=?",String.class,booking)).isEqualTo("Nome preservado");
            jdbc.update("delete from professional_service where business_id=? and professional_id=? and service_id=?",a,p,s2);
            next.load().migrate();
            assertThat(jdbc.queryForObject("select count(*) from professional_service",Integer.class)).isEqualTo(4);
            jdbc.update("update \"flyway_schema_history\" set \"version\"='10' where \"version\"='7'");
            assertThatThrownBy(()->{try(var connection=jdbc.getDataSource().getConnection()){br.com.luizcode.agenda.backup.DatabaseSnapshot.inspect(connection,null);}}).hasMessageContaining("esquema incompatível");
        } finally {
            if(postgres)next.cleanDisabled(false).load().clean();
            else jdbc.execute("shutdown");
        }
    }
}
