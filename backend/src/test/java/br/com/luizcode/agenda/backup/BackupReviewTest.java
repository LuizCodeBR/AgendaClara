package br.com.luizcode.agenda.backup;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;

class BackupReviewTest {
    @TempDir Path directory;
    static final String SECRET="senha ficticia exclusiva para revisao";
    BackupTest.Fixture fixture() throws Exception {var b=new BackupTest();b.directory=directory;return b.fixture();}
    int run(String action,Path data,Path work,String... extra) {
        var args=new ArrayList<>(List.of("--operations="+action,"--engine=h2","--data-dir="+data,"--work-dir="+work));args.addAll(List.of(extra));
        return OperationsTool.run(args.toArray(String[]::new),Map.of("AGENDA_BACKUP_PASSWORD",SECRET),new PrintStream(new ByteArrayOutputStream()));
    }
    @Test void invalidUsageCannotPreventCreatingAndVerifyingBackup() throws Exception {
        var source=fixture();Path work=directory.resolve("operation"),input=directory.resolve("usage.json");
        Files.writeString(input,"{\"password\":\"private-do-not-print\"}");
        assertThat(run("backup",source.data(),work,"--provider-input="+input)).isEqualTo(2);
        var report=OperationsUsage.JSON.readValue(Files.readAllBytes(work.resolve("latest.json")),OperationsReport.Report.class);
        assertThat(report.backup().status()).isEqualTo("VERIFIED");
        assertThat(Files.readString(work.resolve("latest.html"))).doesNotContain("private-do-not-print");
    }
    @Test void anotherH2DatabaseCannotReuseAHealthyBackupHistory() throws Exception {
        var a=fixture();var b=fixture();Path work=directory.resolve("operation");
        assertThat(run("backup",a.data(),work)).isEqualTo(2);byte[] before=Files.readAllBytes(work.resolve("state.json"));
        assertThat(run("report",b.data(),work)).isEqualTo(1);
        assertThat(Files.readAllBytes(work.resolve("state.json"))).isEqualTo(before);
    }
    @Test void oldExceededObservationKeepsItsSeverity() {
        var metric=OperationsUsage.metric("x","x",110d,100d,"h","console",false);
        assertThat(metric.status()).isEqualTo("EXCEEDED");
    }
    @Test void simultaneousFirstRunsReturnAReportOrBusyWithoutInitializationErrors() throws Exception {
        for(int iteration=0;iteration<3;iteration++) {
            Path root=directory.resolve("concurrent-"+iteration);var start=new CountDownLatch(1);
            try(var pool=Executors.newFixedThreadPool(12)) {
                var futures=new ArrayList<Future<Integer>>();
                for(int i=0;i<12;i++)futures.add(pool.submit(()->{start.await();return run("report",directory.resolve("missing"),root);}));
                start.countDown();
                for(var future:futures)assertThat(future.get(15,TimeUnit.SECONDS)).isIn(2,3);
            }
        }
    }
    @Test @EnabledOnOs(OS.WINDOWS) void windowsJunctionCannotRedirectTheOperationalDirectory() throws Exception {
        Path target=directory.resolve("actual"),link=directory.resolve("junction");Files.createDirectory(target);
        assertThat(target.toAbsolutePath().normalize().startsWith(directory.toAbsolutePath().normalize())).isTrue();
        String script="New-Item -ItemType Junction -Path '"+link.toString().replace("'","''")+"' -Target '"+target.toString().replace("'","''")+"' | Out-Null";
        var child=new ProcessBuilder("powershell.exe","-NoProfile","-Command",script).redirectErrorStream(true).start();
        assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isZero();
        try{assertThatThrownBy(()->OperationsTool.workspace(link)).isInstanceOf(IOException.class);}
        finally{Files.deleteIfExists(link);}
        assertThat(target.resolve(".agenda-operations")).doesNotExist();
    }

    static final class SimulatedCrash extends Error {}
    @Test void durableIntentMakesAnInterruptedAttemptVisibleWithoutLosingLastGoodCopy() throws Exception {
        var source=fixture();Path work=directory.resolve("operation");run("backup",source.data(),work);
        var before=OperationsTool.state(work).lastVerified();
        String[] args={"--operations=backup","--engine=h2","--data-dir="+source.data(),"--work-dir="+work};
        assertThatThrownBy(()->OperationsTool.run(args,Map.of("AGENDA_BACKUP_PASSWORD",SECRET),new PrintStream(new ByteArrayOutputStream()),Clock.systemUTC(),
            (engine,data,archive,password,env)->{
                assertThat(OperationsTool.state(work).lastAttempt().result()).isEqualTo("RUNNING");
                throw new SimulatedCrash();
            })).isInstanceOf(SimulatedCrash.class);
        assertThat(run("report",source.data(),work)).isEqualTo(2);
        var after=OperationsTool.state(work);
        assertThat(after.lastVerified()).isEqualTo(before);
        assertThat(after.lastAttempt().result()).isEqualTo("INTERRUPTED");
        assertThat(work.resolve("backups").resolve(before.filename())).exists();
        assertThat(Files.readString(work.resolve("latest.html"))).contains("Tentativa interrompida");
    }
    @Test void legacyHistoryRemainsUnboundUntilANewVerifiedBackupEstablishesItsSource() throws Exception {
        var source=fixture();Path work=directory.resolve("operation");run("backup",source.data(),work);
        var current=OperationsTool.state(work);var previous=current.lastVerified();
        OperationsTool.save(work,new OperationsReport.State(1,previous,current.lastAttempt()));
        assertThat(run("report",source.data(),work)).isEqualTo(2);
        var report=OperationsUsage.JSON.readValue(Files.readAllBytes(work.resolve("latest.json")),OperationsReport.Report.class);
        assertThat(report.backup().status()).isEqualTo("UNBOUND");assertThat(report.backup().sourceBound()).isFalse();
        assertThat(run("backup",source.data(),work)).isEqualTo(2);
        assertThat(OperationsTool.state(work).targetId()).matches("[a-f0-9]{64}");
        assertThat(OperationsTool.state(work).format()).isEqualTo(2);
        assertThat(work.resolve("backups").resolve(previous.filename())).exists();
    }
    @Test void nativeEnvironmentDropsAmbientDestinationsAndApplicationSecrets() throws Exception {
        var configuration=new HashMap<String,String>();configuration.put("DB_URL","jdbc:postgresql://db.example.test/agenda?channelBinding=require");
        configuration.put("DB_USER","owner");configuration.put("DB_PASSWORD","fake-database-password");
        configuration.put("PGHOSTADDR","192.0.2.1");configuration.put("PGSERVICE","other-server");configuration.put("PGOPTIONS","-c search_path=other");
        configuration.put("MAIL_ENCRYPTION_KEY","fake-mail-key");configuration.put("AGENDA_BACKUP_PASSWORD",SECRET);configuration.put("RESEND_API_KEY","fake-mail-provider");
        var pg=DatabaseSnapshot.pg(configuration);var nativeEnv=BackupPostgres.environment(pg,configuration);
        assertThat(nativeEnv).containsEntry("PGHOST","db.example.test").containsEntry("PGSSLMODE","require").containsEntry("PGCHANNELBINDING","require");
        assertThat(nativeEnv).doesNotContainKeys("PGHOSTADDR","PGSERVICE","PGOPTIONS","MAIL_ENCRYPTION_KEY","AGENDA_BACKUP_PASSWORD","RESEND_API_KEY","DB_PASSWORD");
        assertThat(nativeEnv.get("PGPASSWORD")).isEqualTo("fake-database-password");
        assertThat(BackupPostgres.databaseArgument(pg)).doesNotContain("fake-database-password");
    }
    @Test void connectionSettingsCannotDisableTimeoutsOrSupplyAnAlternateRoleOrEndpoint() throws Exception {
        var env=new HashMap<String,String>();env.put("DB_USER","owner");env.put("DB_PASSWORD","private");
        env.put("DB_URL","jdbc:postgresql://127.0.0.1/agenda?socketTimeout=0&connectTimeout=0&loginTimeout=0");
        String url=BackupPostgres.jdbcUrl(DatabaseSnapshot.pg(env),env);
        assertThat(url).contains("socketTimeout=20","connectTimeout=10","loginTimeout=15").doesNotContain("Timeout=0");
        for(String query:List.of("user=another","password=do-not-log","socketFactory=other","sslmode=require&sslmode=disable")) {
            env.put("DB_URL","jdbc:postgresql://db.example.test/agenda?"+query);
            assertThatThrownBy(()->DatabaseSnapshot.pg(env)).isInstanceOf(IOException.class);
        }
        env.put("DB_URL","jdbc:postgresql://db.example.test/agenda?sslmode=disable");
        assertThatThrownBy(()->DatabaseSnapshot.pg(env)).hasMessageContaining("TLS");
    }
    @Test void specialDatabaseNamesRemainALiteralDatabaseNameForLibpq() throws Exception {
        var pg=DatabaseSnapshot.pg(Map.of("DB_URL","jdbc:postgresql://127.0.0.1/agenda%27%20host%3Dother%5Cname","DB_USER","owner"));
        assertThat(BackupPostgres.databaseArgument(pg)).isEqualTo("dbname='agenda\\' host=other\\\\name'");
        assertThat(BackupPostgres.database(pg)).isEqualTo("agenda' host=other\\name");
    }
    @Test void sourceBindingNormalizesPortsButDoesNotIncludePasswordsOrTlsOptions() throws Exception {
        var one=Map.of("DB_URL","jdbc:postgresql://DB.EXAMPLE.TEST/agenda","DB_USER","owner","DB_PASSWORD","one");
        var two=Map.of("DB_URL","jdbc:postgresql://db.example.test:5432/agenda?sslmode=verify-full","DB_USER","owner","DB_PASSWORD","two");
        String id=OperationsTool.sourceId("postgres",directory,one);
        assertThat(id).isEqualTo(OperationsTool.sourceId("postgres",directory,two));
        assertThat(id).isNotEqualTo(OperationsTool.sourceId("postgres",directory,Map.of("DB_URL","jdbc:postgresql://db.example.test/other","DB_USER","owner")));
    }
    @Test void invalidMarkerDoesNotChangeAnUnrelatedFoldersPermissionsOrContents() throws Exception {
        Path root=directory.resolve("unrelated");Files.createDirectory(root);Files.writeString(root.resolve(".agenda-operations"),"not-ours");
        var acl=Files.getFileAttributeView(root,java.nio.file.attribute.AclFileAttributeView.class);
        Object before=acl==null?Files.getPosixFilePermissions(root):acl.getAcl();
        assertThat(run("report",directory,root)).isEqualTo(1);
        assertThat(acl==null?Files.getPosixFilePermissions(root):acl.getAcl()).isEqualTo(before);
        try(var list=Files.list(root)){assertThat(list.map(p->p.getFileName().toString()).toList()).containsExactly(".agenda-operations");}
    }
    @Test void privateFilesAreBornWithOwnerOnlyAccess() throws Exception {
        Path root=directory.resolve("private");BackupFiles.directory(root);Path file=BackupFiles.file(root.resolve("secret"));
        if(Files.getFileStore(file).supportsFileAttributeView("posix"))
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        else {
            var acl=Files.getFileAttributeView(file,java.nio.file.attribute.AclFileAttributeView.class).getAcl();
            assertThat(acl).hasSize(1);assertThat(acl.getFirst().principal()).isEqualTo(Files.getOwner(file));
        }
    }
    public static class ProcessProbe {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[3]),"ready");long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(!Files.exists(Path.of(args[2]))) {if(System.nanoTime()>until)System.exit(77);Thread.sleep(10);}
            System.exit(OperationsTool.run(new String[]{"--operations=report","--work-dir="+args[0],"--data-dir="+args[1]},Map.of(),System.out));
        }
    }
    @Test void separateJvmsCoordinateTheFirstInitializationUsingTheSameOsLock() throws Exception {
        Path root=directory.resolve("process-root"),gate=directory.resolve("gate");var children=new ArrayList<Process>();
        try {
            for(int i=0;i<2;i++) {
                Path ready=directory.resolve("ready-"+i);
                children.add(new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Djava.io.tmpdir="+System.getProperty("java.io.tmpdir"),"-cp",System.getProperty("java.class.path"),
                    ProcessProbe.class.getName(),root.toString(),directory.resolve("missing").toString(),gate.toString(),ready.toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("child-"+i+".log").toFile()).start());
            }
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(!Files.exists(directory.resolve("ready-0"))||!Files.exists(directory.resolve("ready-1"))) {
                if(System.nanoTime()>until)throw new AssertionError("Child processes did not become ready");Thread.sleep(10);
            }
            Files.writeString(gate,"go");
            for(Process child:children){assertThat(child.waitFor(15,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isIn(2,3);}
        } finally {for(Process child:children)if(child.isAlive()){child.destroyForcibly();child.waitFor(3,TimeUnit.SECONDS);}}
    }
    @Test @EnabledOnOs(OS.LINUX) void nonRegularArchiveIsRefusedWithoutOpeningAPipe() throws Exception {
        Path pipe=directory.resolve("pipe");
        var child=new ProcessBuilder("mkfifo",pipe.toString()).start();assertThat(child.waitFor(5,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isZero();
        assertThatThrownBy(()->BackupArchive.read(pipe,SECRET.toCharArray())).isInstanceOf(IOException.class);
    }


    @Test void archiveHashAcceptsTheAuthenticatedEnvelopeAboveTheContentLimit() throws Exception {
        Path file=directory.resolve("maximum-envelope.acbackup");
        try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            channel.position((long)BackupArchive.LIMIT+51);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        assertThat(OperationsTool.hash(file)).matches("[a-f0-9]{64}");
    }

}
