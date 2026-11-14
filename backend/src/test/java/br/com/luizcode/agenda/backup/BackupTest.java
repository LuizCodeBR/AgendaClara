package br.com.luizcode.agenda.backup;

import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.identity.MailSettings;
import br.com.luizcode.agenda.mail.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import javax.crypto.Cipher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

class BackupTest {
    @TempDir Path directory;
    static final char[] PASSWORD="frase exclusiva para backup ficticio".toCharArray();
    record Fixture(Path data,UUID company,UUID user,UUID mail,String aad) {}
    Fixture fixture() throws Exception {
        Path data=directory.resolve("source-"+UUID.randomUUID());Files.createDirectories(data);
        String url="jdbc:h2:file:"+data.resolve("agenda")+";DB_CLOSE_ON_EXIT=FALSE";
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        var cipher=new MailPayloadCipher(jdbc,new ObjectMapper(),new MailSettings("demo","","","",true,false,false),Clock.systemUTC(),"",data.resolve("mail-outbox.key").toString());
        UUID business=UUID.randomUUID(),user=UUID.randomUUID(),mail=UUID.randomUUID();
        jdbc.update("insert into business(id,slug,name,category,zone_id,address) values(?,?,?,?,?,?)",business,"restore-"+business,"Empresa preservada","Teste","America/Sao_Paulo","Online");
        jdbc.update("insert into app_user(id,business_id,email,password_hash,display_name) values(?,?,?,?,?)",user,business,"backup@example.test","hash-preservado","Ana");
        UUID person=UUID.randomUUID(),service=UUID.randomUUID();
        jdbc.update("insert into professional(id,business_id,name,specialty) values (?,?,?,?)",person,business,"Pessoa preservada","Teste");
        jdbc.update("insert into service_offering(id,business_id,name,description,duration_minutes,price_cents) values (?,?,?,?,30,1200)",service,business,"Serviço preservado","Teste");
        jdbc.update("insert into professional_service(business_id,professional_id,service_id) values (?,?,?)",business,person,service);
        String aad=mail+":"+business+":"+user+":VERIFY:demo";
        String payload=cipher.encrypt(aad,new MailQueueModels.Payload("from","backup@example.test","Assunto","Texto preservado","https://example.test/#/token"));
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into mail_outbox(id,business_id,user_id,purpose,delivery_mode,status,payload,key_id,created_at,expires_at,send_deadline,accepted_at) values(?,?,?,'VERIFY','demo','ACCEPTED',?,?,?,?,?,?)",
                mail,business,user,payload,cipher.keyId(),now,now.plusHours(24),now.plusHours(23),now);
        return new Fixture(data,business,user,mail,aad);
    }
    Path archive(Fixture fixture) throws Exception {
        Path output=directory.resolve(UUID.randomUUID()+".acbackup");
        DatabaseSnapshot.createLocal(fixture.data(),output,PASSWORD,Map.of());return output;
    }
    @Test void localRoundTripPreservesAccountsAndDecryptsTheSameMailWithOriginalKey() throws Exception {
        var fixture=fixture();Path archive=archive(fixture),target=directory.resolve("restored");
        try(var bundle=BackupArchive.read(archive,PASSWORD)) {
            assertThat(bundle.manifest().engine()).isEqualTo("h2");DatabaseSnapshot.restoreLocal(bundle,target);
        }
        String url="jdbc:h2:file:"+target.resolve("agenda");
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        assertThat(jdbc.queryForObject("select name from business where id=?",String.class,fixture.company())).isEqualTo("Empresa preservada");
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,fixture.user())).isEqualTo("hash-preservado");
        var cipher=new MailPayloadCipher(jdbc,new ObjectMapper(),new MailSettings("demo","","","",true,false,false),Clock.systemUTC(),"",target.resolve("mail-outbox.key").toString());
        assertThat(jdbc.queryForObject("select count(*) from professional_service where business_id=?",Integer.class,fixture.company())).isEqualTo(1);
        String payload=jdbc.queryForObject("select payload from mail_outbox where id=?",String.class,fixture.mail());
        assertThat(cipher.decrypt(fixture.aad(),payload).text()).isEqualTo("Texto preservado");
        assertThat(new String(Files.readAllBytes(archive),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Texto preservado");
    }
    @Test void runningDatabaseIsRefusedWithoutPublishingAnArchive() throws Exception {
        var fixture=fixture();Path output=directory.resolve("blocked.acbackup");
        try(var connection=DriverManager.getConnection("jdbc:h2:file:"+fixture.data().resolve("agenda"),"sa","")) {
            assertThatThrownBy(()->DatabaseSnapshot.createLocal(fixture.data(),output,PASSWORD,Map.of())).isInstanceOf(IOException.class);
        }
        assertThat(output).doesNotExist();
    }
    @Test void wrongPasswordTamperingAndTruncationFailAuthentication() throws Exception {
        Path output=directory.resolve("protected.acbackup");
        BackupArchive.write(output,"h2","test","6",null,Map.of("agenda.mv.db","private".getBytes()),PASSWORD);
        assertThatThrownBy(()->BackupArchive.read(output,"senha incorreta".toCharArray())).isInstanceOf(Exception.class);
        byte[] bytes=Files.readAllBytes(output);bytes[bytes.length-2]^=1;Path changed=directory.resolve("changed");Files.write(changed,bytes);
        assertThatThrownBy(()->BackupArchive.read(changed,PASSWORD)).isInstanceOf(Exception.class);
        Files.write(changed,Arrays.copyOf(bytes,bytes.length-10));
        assertThatThrownBy(()->BackupArchive.read(changed,PASSWORD)).isInstanceOf(Exception.class);
    }
    @Test void existingBackupAndRestoreDirectoryAreNeverReplaced() throws Exception {
        var fixture=fixture();Path archive=archive(fixture);
        byte[] before=Files.readAllBytes(archive);
        assertThatThrownBy(()->DatabaseSnapshot.createLocal(fixture.data(),archive,PASSWORD,Map.of())).isInstanceOf(Exception.class);
        assertThat(Files.readAllBytes(archive)).isEqualTo(before);
        byte[] database=Files.readAllBytes(fixture.data().resolve("agenda.mv.db"));
        try(var bundle=BackupArchive.read(archive,PASSWORD)) {
            assertThatThrownBy(()->DatabaseSnapshot.restoreLocal(bundle,fixture.data())).isInstanceOf(FileAlreadyExistsException.class);
        }
        assertThat(Files.readAllBytes(fixture.data().resolve("agenda.mv.db"))).isEqualTo(database);
    }
    @Test void missingAndWrongOriginalKeysCannotCreateApparentlyValidBackups() throws Exception {
        var fixture=fixture();Path output=directory.resolve("wrong.acbackup");
        assertThatThrownBy(()->DatabaseSnapshot.createLocal(fixture.data(),output,PASSWORD,Map.of("MAIL_ENCRYPTION_KEY","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")))
                .hasMessageContaining("chave original");
        assertThat(output).doesNotExist();
        Files.delete(fixture.data().resolve("mail-outbox.key"));
        assertThatThrownBy(()->DatabaseSnapshot.createLocal(fixture.data(),output,PASSWORD,Map.of())).hasMessageContaining("chave original");
        assertThat(output).doesNotExist();
    }
    @Test void unexpectedArchivePathsAreRefusedBeforeAnyFileIsExtracted() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("../escape"));zip.write("untrusted".getBytes());zip.closeEntry();}
        byte[] salt=new byte[16],nonce=new byte[12];
        var output=new ByteArrayOutputStream();output.write("ACBK0001".getBytes());output.write(salt);output.write(nonce);
        output.write(BackupArchive.crypt(Cipher.ENCRYPT_MODE,bytes.toByteArray(),PASSWORD,salt,nonce));
        Path path=directory.resolve("path.acbackup");Files.write(path,output.toByteArray());
        assertThatThrownBy(()->BackupArchive.read(path,PASSWORD)).hasMessageContaining("inesperado");
        assertThat(directory.resolve("escape")).doesNotExist();
    }
    @Test void oversizedInputsAreRefusedBeforeLoadingTheFile() throws Exception {
        Path path=directory.resolve("large.acbackup");
        try(var file=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){file.position(BackupArchive.LIMIT+53L);file.write(ByteBuffer.wrap(new byte[]{0}));}
        assertThatThrownBy(()->BackupArchive.read(path,PASSWORD)).hasMessageContaining("limite");
    }
    @Test void independentBackupsUseDifferentSaltAndNonceAndCliNeverPrintsPassword() throws Exception {
        Path one=directory.resolve("one"),two=directory.resolve("two");
        var files=Map.of("agenda.mv.db","private".getBytes());
        BackupArchive.write(one,"h2","test","6",null,files,PASSWORD);BackupArchive.write(two,"h2","test","6",null,files,PASSWORD);
        assertThat(Files.readAllBytes(one)).isNotEqualTo(Files.readAllBytes(two));
        var text=new ByteArrayOutputStream();
        int code=BackupTool.run(new String[]{"--backup=verify","--input="+one},Map.of("AGENDA_BACKUP_PASSWORD","wrong-secret"),new PrintStream(text));
        assertThat(code).isEqualTo(1);assertThat(text.toString()).doesNotContain("wrong-secret");
    }
    @Test void cliHelpWorksWithoutPasswordAndRejectsUnknownOptions() {
        var output=new ByteArrayOutputStream();var printer=new PrintStream(output);
        assertThat(BackupTool.run(new String[]{"--backup=help"},Map.of(),printer)).isZero();
        assertThat(BackupTool.run(new String[]{"--backup=verify","--password=do-not-print"},Map.of(),printer)).isEqualTo(1);
        assertThat(output.toString()).doesNotContain("do-not-print");
    }
}
