package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AccountAccessMigrationTest {
    @Test void versionFourAccountsKeepAccessWithoutFalselyClaimingEmailWasVerified() {
        String url="jdbc:h2:mem:access-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("4").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        UUID business=UUID.randomUUID(), user=UUID.randomUUID();
        jdbc.update("insert into business(id,slug,name,category,zone_id,address) values(?,?,?,?,?,?)",business,"legacy","Empresa antiga","Teste","Online","Local");
        jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name) values(?,?,?,?,?)",user,business,"legacy@example.test","existing-hash","Ana");
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,user)).isEqualTo("existing-hash");
        assertThat(jdbc.queryForObject("select credentials_version from app_user where id=?",Integer.class,user)).isZero();
        assertThat(jdbc.queryForObject("select email_verification_required from app_user where id=?",Boolean.class,user)).isFalse();
        assertThat(jdbc.queryForObject("select email_verified_at from app_user where id=?",java.time.OffsetDateTime.class,user)).isNull();
        assertThat(jdbc.queryForObject("select public_booking_enabled from business where id=?",Boolean.class,business)).isTrue();
    }
}
