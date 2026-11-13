package br.com.luizcode.agenda.backup;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class OperationsTest {
    @TempDir Path directory;
    static final String SECRET="senha ficticia exclusiva da operacao";
    Path work(){return directory.resolve("operation");}
    BackupTest.Fixture fixture() throws Exception {
        var backup=new BackupTest();backup.directory=directory;return backup.fixture();
    }
    int run(String action,Path data,Map<String,String> extra) {
        var env=new HashMap<String,String>();env.put("AGENDA_BACKUP_PASSWORD",SECRET);env.putAll(extra);
        return OperationsTool.run(new String[]{"--operations="+action,"--engine=h2","--data-dir="+data,"--work-dir="+work()},env,new PrintStream(new ByteArrayOutputStream()));
    }
    OperationsReport.Report report() throws Exception {
        return OperationsUsage.JSON.readValue(Files.readAllBytes(work().resolve("latest.json")),OperationsReport.Report.class);
    }
    @Test void pipelineCreatesAuthenticatedBackupAndRecordsOnlyVerifiedResult() throws Exception {
        var source=fixture();assertThat(run("backup",source.data(),Map.of())).isEqualTo(2);
        var result=report();assertThat(result.backup().status()).isEqualTo("VERIFIED");
        var verified=result.backup().lastVerified();Path archive=work().resolve("backups").resolve(verified.filename());
        assertThat(verified.sha256()).isEqualTo(OperationsTool.hash(archive));
        try(var bundle=BackupArchive.read(archive,SECRET.toCharArray())) {
            assertThat(bundle.manifest().engine()).isEqualTo("h2");
            DatabaseSnapshot.restoreLocal(bundle,directory.resolve("restored"));
        }
        assertThat(result.onDemandOnly()).isTrue();
        String json=Files.readString(work().resolve("latest.json")),html=Files.readString(work().resolve("latest.html"));
        assertThat(json+html).doesNotContain(SECRET,"backup@example.test","hash-preservado","mail-outbox.key","jdbc:h2:");
        assertThat(html).contains("Não informado","Content-Security-Policy","Sem agendamento");
    }
    @Test void failedAttemptPreservesLastGoodArchiveAndRecordsFailure() throws Exception {
        var source=fixture();run("backup",source.data(),Map.of());
        var before=report().backup().lastVerified();Path archive=work().resolve("backups").resolve(before.filename());
        byte[] bytes=Files.readAllBytes(archive);
        try(var file=FileChannel.open(source.data().resolve("agenda.mv.db"),StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=file.lock()) {
            assertThat(run("backup",source.data(),Map.of())).isEqualTo(1);
        }
        var after=report();assertThat(after.backup().lastVerified()).isEqualTo(before);
        assertThat(after.backup().lastAttempt().result()).isEqualTo("FAILED");
        assertThat(Files.readAllBytes(archive)).isEqualTo(bytes);
    }
    @Test void overlappingRunReturnsBusyWithoutStartingOrChangingState() throws Exception {
        Path root=OperationsTool.workspace(work());
        try(var channel=FileChannel.open(root.resolve(".operations.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            assertThat(run("backup",directory.resolve("missing"),Map.of())).isEqualTo(3);
        }
        assertThat(root.resolve("state.json")).doesNotExist();
        try(var paths=Files.list(root.resolve("backups"))){assertThat(paths.count()).isZero();}
    }
    @Test void reportDoesNotCreateCopyOrModifyVerifiedStateAndDetectsTampering() throws Exception {
        var source=fixture();run("backup",source.data(),Map.of());byte[] state=Files.readAllBytes(work().resolve("state.json"));
        assertThat(run("report",source.data(),Map.of())).isEqualTo(2);
        assertThat(Files.readAllBytes(work().resolve("state.json"))).isEqualTo(state);
        assertThat(report().backup().count()).isEqualTo(1);
        Path archive=work().resolve("backups").resolve(report().backup().lastVerified().filename());
        byte[] corrupt=Files.readAllBytes(archive);corrupt[corrupt.length-1]^=1;Files.write(archive,corrupt);
        assertThat(run("report",source.data(),Map.of())).isEqualTo(2);
        assertThat(report().backup().status()).isEqualTo("MISMATCH");
        assertThat(Files.readAllBytes(work().resolve("state.json"))).isEqualTo(state);
    }
    @Test void existingUnrelatedFolderAndMaliciousSavedPathAreRefused() throws Exception {
        Files.createDirectories(work());Path unrelated=work().resolve("keep.txt");Files.writeString(unrelated,"preservar");
        assertThat(run("report",directory,Map.of())).isEqualTo(1);assertThat(Files.readString(unrelated)).isEqualTo("preservar");
        Path owned=directory.resolve("owned");OperationsTool.workspace(owned);
        String json=OperationsUsage.JSON.writeValueAsString(new OperationsReport.State(1,new OperationsReport.Verified("../outside.acbackup","h2",Instant.now().toString(),Instant.now().toString(),1,"a".repeat(64)),null));
        Files.writeString(owned.resolve("state.json"),json);
        var out=new ByteArrayOutputStream();
        assertThat(OperationsTool.run(new String[]{"--operations=report","--work-dir="+owned},Map.of(),new PrintStream(out))).isEqualTo(1);
        assertThat(out.toString()).doesNotContain("../outside");
    }
    @Test void quotaThresholdsDistinguishWarningCriticalExceededAndUnknown() {
        assertThat(OperationsUsage.metric("x","x",69d,100d,"h","console",true).status()).isEqualTo("OK");
        assertThat(OperationsUsage.metric("x","x",70d,100d,"h","console",true).status()).isEqualTo("WARNING");
        assertThat(OperationsUsage.metric("x","x",90d,100d,"h","console",true).status()).isEqualTo("CRITICAL");
        assertThat(OperationsUsage.metric("x","x",100d,100d,"h","console",true).status()).isEqualTo("EXCEEDED");
        assertThat(OperationsUsage.metric("x","x",null,100d,"h","console",true).percentage()).isNull();
        assertThat(OperationsUsage.metric("x","x",null,100d,"h","console",true).status()).isEqualTo("UNKNOWN");
    }
    OperationsUsage.Snapshot snapshot(Instant observed) {
        var start=observed.atZone(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        var end=start.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        return new OperationsUsage.Snapshot(1,observed.toString(),"console",
            new OperationsUsage.Neon("project-test","free",0d,0d,0d,start.toString(),end.toString()),
            new OperationsUsage.Render("workspace-test","hobby",0d,0d,0d,500d,start.toString(),end.toString()));
    }
    @Test void recentZeroIsDifferentFromMissingButExpiredPeriodAndStaleObservationNeverLookHealthy() {
        Instant observed=Instant.parse("2026-11-30T23:00:00Z");var snapshot=snapshot(observed);
        assertThat(OperationsUsage.metrics(snapshot,10L,"postgres",observed).stream().filter(m->m.id().equals("neon-compute")).findFirst().orElseThrow().status()).isEqualTo("OK");
        Instant nextMonth=Instant.parse("2026-12-01T01:00:00Z");
        assertThat(OperationsUsage.metrics(snapshot,10L,"postgres",nextMonth).stream().filter(m->m.id().equals("neon-compute")).findFirst().orElseThrow().status()).isEqualTo("STALE");
        assertThat(OperationsUsage.fresh(snapshot,observed.plus(Duration.ofHours(25)))).isFalse();
        assertThat(OperationsUsage.metrics(null,10L,"postgres",observed).stream().filter(m->m.id().equals("neon-compute")).findFirst().orElseThrow().status()).isEqualTo("UNKNOWN");
    }
    @Test void providerInputRejectsSecretsUnexpectedFieldsWrongResourceNegativeAndFutureValues() throws Exception {
        Instant now=Instant.now();Path input=directory.resolve("input.json");
        var valid=snapshot(now);Files.writeString(input,OperationsUsage.JSON.writeValueAsString(valid));
        assertThat(OperationsUsage.read(input,Map.of("NEON_PROJECT_ID","project-test"),now)).isEqualTo(valid);
        assertThatThrownBy(()->OperationsUsage.read(input,Map.of("NEON_PROJECT_ID","wrong-project"),now)).hasMessageContaining("outro recurso");
        String json=Files.readString(input);Files.writeString(input,json.substring(0,json.length()-1)+",\"password\":\"do-not-log\"}");
        assertThatThrownBy(()->OperationsUsage.read(input,Map.of(),now)).isInstanceOf(Exception.class);
        Files.writeString(input,OperationsUsage.JSON.writeValueAsString(snapshot(now.plusSeconds(600))));
        assertThatThrownBy(()->OperationsUsage.read(input,Map.of(),now)).hasMessageContaining("futuro");
        assertThatThrownBy(()->OperationsUsage.numbers(-1d)).hasMessageContaining("inválido");
        assertThatThrownBy(()->OperationsUsage.numbers(Double.NaN)).hasMessageContaining("inválido");
        Files.writeString(input,"x".repeat(16385));assertThatThrownBy(()->OperationsUsage.read(input,Map.of(),now)).hasMessageContaining("16 KiB");
    }
    @Test void htmlEscapesMarkupAndNoOperationIsConfusedWithTheWebServer() {
        assertThat(OperationsReport.escape("<script>\"'&")).isEqualTo("&lt;script&gt;&quot;&#39;&amp;");
        assertThat(OperationsTool.requested(new String[]{"--operations=report"})).isTrue();
        var out=new ByteArrayOutputStream();
        assertThat(OperationsTool.run(new String[]{"--operations=help"},Map.of(),new PrintStream(out))).isZero();
        assertThat(out.toString()).contains("Nenhum agendamento");
        assertThat(OperationsTool.run(new String[]{"--operations=report","--password=secret"},Map.of(),new PrintStream(out))).isEqualTo(1);
        assertThat(out.toString()).doesNotContain("secret");
    }
    @Test void symbolicOperationalDirectoryIsRefused() throws Exception {
        Path target=directory.resolve("actual");Files.createDirectory(target);Path link=directory.resolve("link");
        try{Files.createSymbolicLink(link,target);}catch(IOException|UnsupportedOperationException ex){Assumptions.assumeTrue(false,"Sistema não permite criar link simbólico neste teste.");}
        assertThatThrownBy(()->OperationsTool.workspace(link)).hasMessageContaining("simbólicos");
        assertThat(target.resolve(".agenda-operations")).doesNotExist();
    }
}
