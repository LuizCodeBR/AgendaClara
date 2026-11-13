package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RegistrationMigrationTest {
    @Test void upgradingVersionThreePreservesPublishedCompanyAndLegacyLoginWithoutRegistrationKey() {
        String url="jdbc:h2:mem:registration-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("3").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        UUID business=UUID.randomUUID(), user=UUID.randomUUID();
        jdbc.update("insert into business(id,slug,name,category,zone_id,address) values (?,?,?,?,?,?)",
                business,"existing","Empresa existente","Teste","America/Sao_Paulo","Online");
        jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name) values (?,?,?,?,?)",
                user,business,"existing@example.test","legacy-hash","Responsável existente");
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        assertThat(jdbc.queryForObject("select public_booking_enabled from business where id=?",Boolean.class,business)).isTrue();
        assertThat(jdbc.queryForObject("select publication_version from business where id=?",Integer.class,business)).isZero();
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,user)).isEqualTo("legacy-hash");
        assertThat(jdbc.queryForObject("select registration_key from app_user where id=?",UUID.class,user)).isNull();
    }
}
