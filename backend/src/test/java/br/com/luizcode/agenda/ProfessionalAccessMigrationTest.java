package br.com.luizcode.agenda;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
class ProfessionalAccessMigrationTest {
 @Test void upgradePreservesOwnerAndProfessionalWithoutInventingAccounts()throws Exception{
  String configured=System.getenv("TEST_DB_URL");boolean pg=configured!=null&&configured.startsWith("jdbc:postgresql:");
  String url=pg?configured:"jdbc:h2:mem:staff-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
  String user=pg?System.getenv().getOrDefault("TEST_DB_USER","agenda"):"sa",password=pg?System.getenv().getOrDefault("TEST_DB_PASSWORD",""):"";
  String schema="staff_upgrade_"+UUID.randomUUID().toString().replace("-","");
  var old=Flyway.configure().dataSource(url,user,password).target("12");var next=Flyway.configure().dataSource(url,user,password).target("13");
  if(pg){old.schemas(schema).defaultSchema(schema);next.schemas(schema).defaultSchema(schema);}
  old.load().migrate();var jdbc=new JdbcTemplate(new DriverManagerDataSource(pg?url+(url.contains("?")?"&":"?")+"currentSchema="+schema:url,user,password));
  try{
   UUID b=UUID.randomUUID(),p=UUID.randomUUID(),u=UUID.randomUUID();
   jdbc.update("insert into business(id,slug,name,category,zone_id,address) values(?,?,'Existente','Teste','America/Sao_Paulo','Online')",b,b.toString());
   jdbc.update("insert into professional(id,business_id,name,specialty,whatsapp_phone) values(?,?,'Pessoa existente','Teste','5511999991234')",p,b);
   jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name,credentials_version) values(?,?,'owner@example.test','hash preservado','Responsável existente',7)",u,b);
   next.load().migrate();next.load().migrate();
   assertThat(jdbc.queryForObject("select role from app_user where id=?",String.class,u)).isEqualTo("OWNER");
   assertThat(jdbc.queryForObject("select enabled from app_user where id=?",Boolean.class,u)).isTrue();
   assertThat(jdbc.queryForObject("select credentials_version from app_user where id=?",Integer.class,u)).isEqualTo(7);
   assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,u)).isEqualTo("hash preservado");
   assertThat(jdbc.queryForObject("select whatsapp_phone from professional where id=?",String.class,p)).isEqualTo("5511999991234");
   assertThat(jdbc.queryForObject("select count(*) from professional_access",Integer.class)).isZero();
   assertThat(jdbc.queryForObject("select count(*) from app_user where professional_id is not null",Integer.class)).isZero();
   try(var c=jdbc.getDataSource().getConnection()){assertThat(br.com.luizcode.agenda.backup.DatabaseSnapshot.inspect(c,null).schema()).isEqualTo("13");}
  }finally{if(pg)next.cleanDisabled(false).load().clean();else jdbc.execute("shutdown");}
 }
}
