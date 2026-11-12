package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DateHoursMigrationTest {
    @Test void upgradingVersionTwoPreservesBusinessProfessionalAndWeeklyHours() {
        String url="jdbc:h2:mem:upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("2").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        UUID business=UUID.randomUUID(), person=UUID.randomUUID();
        jdbc.update("insert into business(id,slug,name,category,zone_id,address) values (?,?,?,?,?,?)",
                business,"upgrade","Negócio existente","Teste","America/Sao_Paulo","Endereço existente");
        jdbc.update("insert into professional(id,business_id,name,specialty,active,version) values (?,?,?,?,TRUE,7)",
                person,business,"Pessoa existente","Especialidade");
        jdbc.update("insert into weekly_hours(id,business_id,professional_id,weekday,opens_at,closes_at) values (?,?,?,2,'09:00','12:00')",
                UUID.randomUUID(),business,person);
        UUID service=UUID.randomUUID(), booking=UUID.randomUUID(), key=UUID.randomUUID();
        jdbc.update("insert into service_offering(id,business_id,name,description,duration_minutes,price_cents) values (?,?,?,?,45,5000)",
                service,business,"Serviço existente","Descrição existente");
        var start=java.time.OffsetDateTime.parse("2030-06-04T12:00:00Z");
        jdbc.update("""
                insert into appointment(id,business_id,professional_id,service_id,service_name,price_cents,customer_name,customer_phone,
                starts_at,ends_at,status,reference,request_key,request_fingerprint,created_at,updated_at)
                values (?,?,?,?,?,5000,'Cliente existente','11999991234',?,?,'BOOKED','UPGRADETEST',?,?,?,?)
                """,booking,business,person,service,"Serviço existente",start,start.plusMinutes(45),key,"a".repeat(64),start.minusDays(1),start.minusDays(1));
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        assertThat(jdbc.queryForObject("select name from business where id=?",String.class,business)).isEqualTo("Negócio existente");
        assertThat(jdbc.queryForObject("select version from professional where id=?",Integer.class,person)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select count(*) from weekly_hours where professional_id=?",Integer.class,person)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select customer_name from appointment where id=?",String.class,booking)).isEqualTo("Cliente existente");
        assertThat(jdbc.queryForObject("select request_key from appointment where id=?",UUID.class,booking)).isEqualTo(key);
        assertThat(jdbc.queryForObject("select count(*) from date_hours",Integer.class)).isZero();
    }
}
