package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.identity.MailSettings;
import br.com.luizcode.agenda.mail.MailPayloadCipher;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

class MailKeyConcurrencyTest {
    @TempDir Path directory;
    @Test void simultaneousFirstStartsShareOneCompleteKeyAndCanDecryptEachOthersMessages() throws Exception {
        for(int round=0;round<3;round++) {
            var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:key-race-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
            jdbc.execute("create table mail_outbox(key_id varchar(64),payload text,expires_at timestamp with time zone)");
            jdbc.execute("create table mail_key_identity(id integer primary key,key_id varchar(64))");
            Path key=directory.resolve("round-"+round).resolve("mail-outbox.key");
            var start=new CountDownLatch(1); var ready=new CountDownLatch(12);
            var settings=new MailSettings("demo","","","",true,false,false);
            var ciphers=new ArrayList<MailPayloadCipher>();
            try(var workers=Executors.newFixedThreadPool(12)) {
                var results=new ArrayList<Future<MailPayloadCipher>>();
                for(int i=0;i<12;i++) results.add(workers.submit(()->{
                    ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return new MailPayloadCipher(jdbc,new ObjectMapper(),settings,Clock.systemUTC(),"",key.toString());
                }));
                assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
                for(var result:results) ciphers.add(result.get(20,TimeUnit.SECONDS));
            }
            assertThat(Files.size(key)).isEqualTo(44);
            assertThat(ciphers.stream().map(MailPayloadCipher::keyId).distinct()).hasSize(1);
            var payload=new br.com.luizcode.agenda.mail.MailQueueModels.Payload("from","to","subject","test","link");
            String encrypted=ciphers.getFirst().encrypt("message-tenant",payload);
            for(var cipher:ciphers) assertThat(cipher.decrypt("message-tenant",encrypted)).isEqualTo(payload);
        }
    }

    @Test void separateJavaProcessesShareTheSameGeneratedKeyFile() throws Exception {
        Path key=directory.resolve("processes").resolve("mail-outbox.key"),start=directory.resolve("start");
        var children=new ArrayList<Process>(); var results=new ArrayList<Path>();
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        try {
            for(int i=0;i<4;i++) {
                Path result=directory.resolve("result-"+i); results.add(result);
                children.add(new ProcessBuilder(java,"-Djava.io.tmpdir="+directory,"-cp",System.getProperty("java.class.path"),
                        KeyProcess.class.getName(),key.toString(),start.toString(),result.toString())
                        .redirectErrorStream(true).redirectOutput(directory.resolve("process-"+i+".log").toFile()).start());
            }
            Files.writeString(start,"go");
            for(var child:children) {
                assertThat(child.waitFor(30,TimeUnit.SECONDS)).isTrue();
                assertThat(child.exitValue()).isZero();
            }
            var identities=new HashSet<String>();
            for(var result:results) identities.add(Files.readString(result));
            assertThat(identities).hasSize(1);
            assertThat(Files.size(key)).isEqualTo(44);
        } finally {
            for(var child:children) if(child.isAlive()) child.destroyForcibly().waitFor(10,TimeUnit.SECONDS);
        }
    }
    public static class KeyProcess {
        public static void main(String[] args) throws Exception {
            Path key=Path.of(args[0]),start=Path.of(args[1]),result=Path.of(args[2]);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.exists(start)) {
                if(System.nanoTime()>deadline) throw new IllegalStateException("start timeout");
                Thread.sleep(10);
            }
            var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:child-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
            jdbc.execute("create table mail_outbox(key_id varchar(64),payload text,expires_at timestamp with time zone)");
            jdbc.execute("create table mail_key_identity(id integer primary key,key_id varchar(64))");
            var cipher=new MailPayloadCipher(jdbc,new ObjectMapper(),new MailSettings("demo","","","",true,false,false),Clock.systemUTC(),"",key.toString());
            Files.writeString(result,cipher.keyId());
        }
    }

}