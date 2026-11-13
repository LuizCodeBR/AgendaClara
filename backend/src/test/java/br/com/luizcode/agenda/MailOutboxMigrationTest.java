package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MailOutboxMigrationTest {
    @Test void upgradingVersionFivePreservesAccountsAndPendingAccessLinks() {
        String url="jdbc:h2:mem:mail-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("5").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        UUID business=UUID.randomUUID(),user=UUID.randomUUID(),action=UUID.randomUUID();
        jdbc.update("insert into business(id,slug,name,category,zone_id,address) values(?,?,?,?,?,?)",business,"upgrade","Empresa existente","Teste","America/Sao_Paulo","Online");
        jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name,credentials_version) values(?,?,?,?,?,4)",user,business,"existing@example.test","preserved-hash","Ana");
        var now=java.time.OffsetDateTime.parse("2030-06-03T10:00:00Z");
        jdbc.update("insert into account_action(id,user_id,purpose,token_digest,simulated,created_at,expires_at) values(?,?,'RESET',?,false,?,?)",action,user,"a".repeat(64),now,now.plusMinutes(15));
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,user)).isEqualTo("preserved-hash");
        assertThat(jdbc.queryForObject("select credentials_version from app_user where id=?",Integer.class,user)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select token_digest from account_action where id=?",String.class,action)).isEqualTo("a".repeat(64));
        assertThat(jdbc.queryForObject("select count(*) from mail_outbox",Integer.class)).isZero();
    }
}
