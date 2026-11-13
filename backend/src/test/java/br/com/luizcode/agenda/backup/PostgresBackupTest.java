package br.com.luizcode.agenda.backup;

import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.identity.MailSettings;
import br.com.luizcode.agenda.mail.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named="AGENDA_PG_CONTAINER",matches=".+")
class PostgresBackupTest {
    @TempDir Path directory;
    @Test void nativeDumpRestoresIntoEmptyDatabaseWithKeyMessagesAndSequence() throws Exception {
        String base=System.getenv("TEST_DB_URL"),user=System.getenv("TEST_DB_USER"),password=System.getenv("TEST_DB_PASSWORD");
        String suffix=UUID.randomUUID().toString().replace("-",""),sourceName="agenda_b_src_"+suffix,targetName="agenda_b_dst_"+suffix;
        String prefix=base.substring(0,base.lastIndexOf('/')+1);
        var created=new ArrayList<String>();
        try(var admin=DriverManager.getConnection(base,user,password)) {
            try {
                for(String name:List.of(sourceName,targetName)) {
                    if(!name.matches("agenda_b_(src|dst)_[a-f0-9]{32}"))throw new IllegalStateException("unsafe test name");
                    try(var statement=admin.createStatement()){statement.execute("create database \""+name+"\"");created.add(name);}
                }
                String source=prefix+sourceName,target=prefix+targetName;
                Flyway.configure().dataSource(source,user,password).load().migrate();
                var jdbc=new JdbcTemplate(new DriverManagerDataSource(source,user,password));
                var cipher=new MailPayloadCipher(jdbc,new ObjectMapper(),new MailSettings("demo","","","",true,false,false),Clock.systemUTC(),"",directory.resolve("mail-outbox.key").toString());
                UUID business=UUID.randomUUID(),owner=UUID.randomUUID(),mail=UUID.randomUUID();
                jdbc.update("insert into business(id,slug,name,category,zone_id,address) values(?,?,?,?,?,?)",business,"native-test","Empresa preservada PG","Teste","America/Sao_Paulo","Online");
                jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name) values(?,?,?,?,?)",owner,business,"native@example.test","hash-preservado","Ana");
                String aad=mail+":"+business+":"+owner+":VERIFY:demo";
                String payload=cipher.encrypt(aad,new MailQueueModels.Payload("from","native@example.test","Teste","Mensagem restaurada","link"));
                var now=OffsetDateTime.now(ZoneOffset.UTC);
                jdbc.update("insert into mail_outbox(id,business_id,user_id,purpose,delivery_mode,status,payload,key_id,created_at,expires_at,send_deadline) values(?,?,?,'VERIFY','demo','ACCEPTED',?,?,?,?,?)",
                        mail,business,owner,payload,cipher.keyId(),now,now.plusDays(1),now.plusHours(23));
                var env=new HashMap<>(System.getenv());env.put("DB_URL",source);env.put("DB_USER",user);env.put("DB_PASSWORD",password);
                env.put("MAIL_KEY_FILE",directory.resolve("mail-outbox.key").toString());env.remove("MAIL_ENCRYPTION_KEY");
                Path archive=directory.resolve("postgres.acbackup");char[] secret="senha exclusiva do teste postgres".toCharArray();
                env.put("AGENDA_BACKUP_PASSWORD",new String(secret));Path operation=directory.resolve("operation");
                assertThat(OperationsTool.run(new String[]{"--operations=backup","--engine=postgres","--data-dir="+directory,"--work-dir="+operation},env,new java.io.PrintStream(new java.io.ByteArrayOutputStream()))).isEqualTo(2);
                var report=new ObjectMapper().readValue(Files.readAllBytes(operation.resolve("latest.json")),OperationsReport.Report.class);
                assertThat(report.databaseBytes()).isPositive();assertThat(report.backup().status()).isEqualTo("VERIFIED");
                archive=operation.resolve("backups").resolve(report.backup().lastVerified().filename());
                env.put("DB_URL",target);Path restoredKey=directory.resolve("restored-key");
                try(var bundle=BackupArchive.read(archive,secret)) {
                    DatabaseSnapshot.restorePostgres(bundle,restoredKey,env);
                    assertThatThrownBy(()->DatabaseSnapshot.restorePostgres(bundle,directory.resolve("another-key"),env)).hasMessageContaining("vazio");
                }
                var restored=new JdbcTemplate(new DriverManagerDataSource(target,user,password));
                assertThat(restored.queryForObject("select name from business where id=?",String.class,business)).isEqualTo("Empresa preservada PG");
                assertThat(Files.readAllBytes(restoredKey.resolve("mail-outbox.key"))).isEqualTo(Files.readAllBytes(directory.resolve("mail-outbox.key")));
                assertThat(cipher.decrypt(aad,restored.queryForObject("select payload from mail_outbox where id=?",String.class,mail)).text()).isEqualTo("Mensagem restaurada");
                restored.update("insert into mail_outbox(id,business_id,user_id,purpose,delivery_mode,status,created_at,expires_at,send_deadline) values(?,?,?,'CHANGED','resend','ACCEPTED',?,?,?)",
                        UUID.randomUUID(),business,owner,now,now.plusHours(1),now.plusHours(1));
                assertThat(restored.queryForObject("select max(queue_seq) from mail_outbox",Long.class)).isEqualTo(2);
            } finally {
                for(String name:created)try(var statement=admin.createStatement()){statement.execute("drop database \""+name+"\"");}
            }
        }
    }
}
