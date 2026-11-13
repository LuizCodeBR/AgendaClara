package br.com.luizcode.agenda.backup;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static br.com.luizcode.agenda.backup.BackupArchive.*;
import static br.com.luizcode.agenda.backup.OperationsReport.*;

/** A bounded, synchronous operator workflow. It creates no recurring task and exposes no HTTP endpoint. */
public final class OperationsTool {
    private OperationsTool() {}
    static final String MARKER="AgendaClaraOperations/v1\n";
    static final long MAX_STATE=65536;
    public static boolean requested(String[] args){return Arrays.stream(args).anyMatch(a->a.startsWith("--operations"));}
    public static int run(String[] args,Map<String,String> env,PrintStream output){return run(args,env,output,Clock.systemUTC());}
    static Map<String,String> options(String[] args) throws IOException {
        var result=new HashMap<String,String>();
        for(String arg:args) {
            int equals=arg.indexOf('=');
            if(!arg.startsWith("--")||equals<3)throw new IOException("Formato de opção inválido.");
            String name=arg.substring(2,equals),value=arg.substring(equals+1);
            if(!Set.of("operations","engine","data-dir","work-dir","provider-input").contains(name)||value.isBlank()||result.put(name,value)!=null)
                throw new IOException("Opção desconhecida ou repetida.");
        }
        return result;
    }
    static void noLinks(Path path) throws IOException { BackupFiles.noLinks(path); }
    static Path workspace(Path path) throws Exception {
        try(var workspace=OperationsWorkspace.acquire(path)) {
            if(!workspace.acquired())throw new IOException("Pasta operacional em uso.");
            return workspace.root();
        }
    }
    static byte[] bounded(Path path,long limit) throws IOException { return BackupFiles.read(path,(int)limit); }
    static void atomic(Path path,byte[] bytes) throws Exception { BackupFiles.atomic(path,bytes); }
    static String sourceId(String engine,Path data,Map<String,String> env) throws Exception {
        String identity;
        if(engine.equals("h2")) {
            Path file=BackupFiles.absolute(data.resolve("agenda.mv.db"));BackupFiles.noLinks(file);
            String name=(Files.exists(file)?file.toRealPath():file).toString();
            if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))name=name.toLowerCase(Locale.ROOT);
            identity="h2\0"+name;
        } else {
            var pg=DatabaseSnapshot.pg(env);var uri=java.net.URI.create(pg.url().substring(5));
            identity="postgres\0"+uri.getHost().toLowerCase(Locale.ROOT)+"\0"+(uri.getPort()<0?5432:uri.getPort())+"\0"+uri.getPath()+"\0"+pg.user();
        }
        return digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static String hash(Path path) throws Exception {
        BackupFiles.noLinks(path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>(long)LIMIT+52)throw new IOException("Backup ausente ou maior que o limite.");
        var digest=MessageDigest.getInstance("SHA-256");long read=0;
        try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1){read+=count;if(read>(long)LIMIT+52)throw new IOException("Backup muito grande.");digest.update(buffer,0,count);}
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static State state(Path root) throws Exception {
        Path path=root.resolve("state.json");
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return new State(2,null,null,null);
        var state=OperationsUsage.JSON.readValue(bounded(path,MAX_STATE),State.class);
        if(!Set.of(1,2).contains(state.format()))throw new IOException("Estado operacional incompatível.");
        if(state.targetId()!=null&&!state.targetId().matches("[a-f0-9]{64}"))throw new IOException("Origem operacional inválida.");
        if(state.format()==2&&state.lastVerified()!=null&&state.targetId()==null)throw new IOException("Origem operacional ausente.");
        if(state.lastVerified()!=null) {
            var v=state.lastVerified();
            if(v.filename()==null||!v.filename().matches("AgendaClara-[0-9TZ-]+-[a-f0-9-]{36}\\.acbackup")||
               !Set.of("h2","postgres").contains(v.engine())||v.bytes()<1||v.bytes()>(long)LIMIT+52||
               v.sha256()==null||!v.sha256().matches("[a-f0-9]{64}"))throw new IOException("Estado operacional inválido.");
            if(Instant.parse(v.createdAt()).isAfter(Instant.parse(v.verifiedAt()).plusSeconds(300)))throw new IOException("Datas operacionais inválidas.");
        }
        if(state.lastAttempt()!=null) {
            var attempt=state.lastAttempt();UUID.fromString(attempt.id());Instant start=Instant.parse(attempt.startedAt());
            String expected=switch(attempt.result()) {case "VERIFIED"->"OK";case "FAILED"->"BACKUP_FAILED";case "RUNNING"->"IN_PROGRESS";case "INTERRUPTED"->"INTERRUPTED";default->throw new IOException("Tentativa operacional inválida.");};
            if(!expected.equals(attempt.code())||(attempt.result().equals("RUNNING")?attempt.finishedAt()!=null:attempt.finishedAt()==null))
                throw new IOException("Tentativa operacional inválida.");
            if(attempt.finishedAt()!=null&&Instant.parse(attempt.finishedAt()).isBefore(start.minusSeconds(300)))throw new IOException("Datas operacionais inválidas.");
        }
        return state;
    }
    static void save(Path root,State state) throws Exception {atomic(root.resolve("state.json"),OperationsUsage.JSON.writeValueAsBytes(state));}
    static char[] password(Map<String,String> env) throws Exception {
        if(env.containsKey("AGENDA_BACKUP_PASSWORD"))return env.get("AGENDA_BACKUP_PASSWORD").toCharArray();
        var console=System.console();
        if(console==null)throw new IOException("Use terminal interativo ou senha no ambiente privado.");
        char[] value=console.readPassword("Senha exclusiva do backup: ");
        char[] confirmation=console.readPassword("Confirme a senha: ");
        try {
            if(value==null||!Arrays.equals(value,confirmation)){if(value!=null)Arrays.fill(value,'\0');throw new IOException("Senhas diferentes.");}
            return value;
        } finally {if(confirmation!=null)Arrays.fill(confirmation,'\0');}
    }
    static Long databaseBytes(String engine,Path data,Map<String,String> env) {
        try {
            if(engine.equals("h2")){Path file=data.resolve("agenda.mv.db");return Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)?Files.size(file):null;}
            var pg=DatabaseSnapshot.pg(env);
            try(var connection=BackupPostgres.connect(pg,env)) {
                connection.setReadOnly(true);
                try(var statement=connection.createStatement()){statement.setQueryTimeout(10);
                    try(var rows=statement.executeQuery("select pg_database_size(current_database())")){return rows.next()?rows.getLong(1):null;}
                }
            }
        } catch(Exception ex){return null;} // Driver errors can contain credentials. No raw error is logged.
    }
    static Report report(Path root,String engine,Path data,Map<String,String> env,State state,OperationsUsage.Snapshot snapshot,boolean invalidUsage,Instant now,String runId) throws Exception {
        var verified=state.lastVerified();boolean matches=false;
        if(verified!=null)try {
            Path path=root.resolve("backups").resolve(verified.filename());
            matches=Files.size(path)==verified.bytes()&&hash(path).equals(verified.sha256());
        }catch(Exception ignored){}
        boolean sourceBound=state.targetId()!=null;
        String backupStatus=verified==null?"NONE":!sourceBound?"UNBOUND":!matches?"MISMATCH":
            Instant.parse(verified.verifiedAt()).isBefore(now.minus(Duration.ofDays(7)))?"STALE":"VERIFIED";
        boolean attemptFailed=state.lastAttempt()!=null&&!state.lastAttempt().result().equals("VERIFIED");
        long count=0,bytes=0;
        try(var list=Files.list(root.resolve("backups"))) {
            for(var iterator=list.iterator();iterator.hasNext();) {Path file=iterator.next();if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&file.getFileName().toString().endsWith(".acbackup")){count++;bytes=Math.addExact(bytes,Files.size(file));}}
        }
        Long dbBytes=databaseBytes(engine,data,env);
        var metrics=OperationsUsage.metrics(snapshot,dbBytes,engine,now);
        var notices=new ArrayList<>(List.of(
            "Execução somente sob demanda, conforme a escolha do usuário. Nenhuma tarefa recorrente está ativa.",
            "A cópia fica no disco desta instalação. Mantenha outra cópia protegida fora dele e a senha separada.",
            "Sete dias sem backup verificado geram atenção; isso é um critério do relatório, não um agendamento.",
            "O arquivo .acbackup aceita até 64 MiB; o limite de 1 GB do provedor não amplia essa capacidade.",
            "Consumo desconhecido, observação com mais de 24 horas ou período encerrado exigem conferência no painel.",
            "O Neon Free não oferece a API detalhada de consumo. Campos zerados de respostas genéricas não foram tratados como prova de uso zero.",
            "As cotas Render são compartilhadas no workspace. Métricas de um serviço não substituem o total de faturamento.",
            "Referências gratuitas consultadas em 13/11/2026. O relatório não altera planos, limites de gastos ou meios de pagamento.",
            "Não há verificação contínua nem aviso externo. Abra ou gere novamente o relatório para consultar mudanças."));
        boolean plansOk=snapshot!=null&&snapshot.neon()!=null&&snapshot.neon().plan().equals("free")&&snapshot.render()!=null&&
            Set.of("free","hobby").contains(snapshot.render().plan())&&OperationsUsage.fresh(snapshot,now);
        if(!plansOk)notices.add("Plano gratuito não confirmado recentemente para ambos os provedores; confira os painéis. Não foi feito upgrade automático.");
        if(dbBytes==null)notices.add("Não foi possível medir o banco atual nesta execução.");
        if(invalidUsage)notices.add("Observação de consumo inválida ou de outro recurso: ignorada. Isso não impede criar uma cópia de segurança.");
        if(verified!=null&&!sourceBound)notices.add("Histórico anterior à vinculação de origem: a cópia foi preservada, mas não é atribuída ao banco atual. Um novo backup válido registra a origem.");
        if(attemptFailed)notices.add("A última tentativa falhou ou foi interrompida. A cópia válida anterior permanece preservada.");
        boolean attention=attemptFailed||invalidUsage||!backupStatus.equals("VERIFIED")||!plansOk||metrics.stream().anyMatch(m->!m.status().equals("OK"));
        var observation=new OperationsUsage.Observation(snapshot==null?null:snapshot.observedAt(),snapshot==null?null:snapshot.source(),
            invalidUsage?"INVALID":snapshot==null?"UNKNOWN":OperationsUsage.fresh(snapshot,now)?"CURRENT":"STALE",snapshot);
        return new Report(2,VERSION,now.toString(),runId,true,engine,attention?"ATTENTION":"OK",
            new Backup(backupStatus,verified,state.lastAttempt(),matches,sourceBound,count,bytes),dbBytes,observation,metrics,List.copyOf(notices));
    }
    @FunctionalInterface interface SnapshotWriter {
        void write(String engine,Path data,Path archive,char[] password,Map<String,String> env) throws Exception;
    }
    static int run(String[] args,Map<String,String> env,PrintStream output,Clock clock) {
        return run(args,env,output,clock,(engine,data,archive,password,configuration)->{
            if(engine.equals("h2"))DatabaseSnapshot.createLocal(data,archive,password,configuration);
            else DatabaseSnapshot.createPostgres(data,archive,password,configuration);
        });
    }
    static int run(String[] args,Map<String,String> env,PrintStream output,Clock clock,SnapshotWriter writer) {
        try {
            var options=options(args);String action=options.getOrDefault("operations","help");
            if(action.equals("help")) {
                output.println("Agenda Clara: --operations=backup|report|help --engine=h2|postgres --data-dir=PASTA --work-dir=PASTA-NOVA [--provider-input=JSON].\n"+
                    "backup: cria, autentica e verifica a cópia, preserva a última válida e gera relatório HTML/JSON.\n"+
                    "report: consulta o último backup e as observações sem criar outra cópia. PostgreSQL usa ambiente privado.\n"+
                    "Nenhum agendamento ou recurso pago é criado. Saídas: 0=conferido; 2=relatório com atenção; 1=falha; 3=outra execução ativa.");
                return 0;
            }
            String engine=options.getOrDefault("engine","h2");
            if(!Set.of("backup","report").contains(action)||!Set.of("h2","postgres").contains(engine))throw new IOException("Operação inválida.");
            Path data=Path.of(options.getOrDefault("data-dir",env.getOrDefault("AGENDA_DATA_DIR","data")));
            String target=sourceId(engine,data,env);
            try(var workspace=OperationsWorkspace.acquire(Path.of(options.getOrDefault("work-dir","operations")))) {
                if(!workspace.acquired()){output.println("ATENÇÃO: outra execução está ativa; nenhuma cópia foi iniciada.");return 3;}
                Path root=workspace.root();State state=state(root);Instant now=clock.instant();String runId=UUID.randomUUID().toString();
                if(state.targetId()!=null&&!state.targetId().equals(target))throw new IOException("Esta pasta pertence a outra origem.");
                if(state.lastVerified()!=null&&(!state.lastVerified().engine().equals(engine)||Instant.parse(state.lastVerified().verifiedAt()).isAfter(now.plusSeconds(300))))
                    throw new IOException("Use o mesmo motor e um estado com data válida.");
                if(state.lastVerified()==null&&state.targetId()==null)state=new State(2,target,null,state.lastAttempt());
                if(state.lastAttempt()!=null&&state.lastAttempt().result().equals("RUNNING")) {
                    var prior=state.lastAttempt();
                    state=new State(state.format(),state.targetId(),state.lastVerified(),new Attempt(prior.id(),prior.startedAt(),now.toString(),"INTERRUPTED","INTERRUPTED"));
                    save(root,state);
                }
                OperationsUsage.Snapshot snapshot=null;boolean invalidUsage=false;
                if(options.containsKey("provider-input"))try{snapshot=OperationsUsage.read(Path.of(options.get("provider-input")),env,now);}
                    catch(Exception ex){invalidUsage=true;}
                boolean failed=false;
                if(action.equals("backup")) {
                    String started=now.toString();char[] password=null;
                    state=new State(state.format(),state.targetId(),state.lastVerified(),new Attempt(runId,started,null,"RUNNING","IN_PROGRESS"));
                    save(root,state); // Durable intent precedes prompts, native processes, copies and encryption.
                    try {
                        if(Files.getFileStore(root).getUsableSpace()<256L*1024*1024)throw new IOException("Espaço insuficiente.");
                        password=password(env);
                        Path archive=root.resolve("backups").resolve("AgendaClara-"+DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now)+"-"+runId+".acbackup");
                        writer.write(engine,data,archive,password,env);
                        try(var bundle=BackupArchive.read(archive,password)) {
                            if(!bundle.manifest().engine().equals(engine))throw new IOException("Tipo de cópia inesperado.");
                            String finished=clock.instant().toString();
                            state=new State(2,target,new Verified(archive.getFileName().toString(),engine,bundle.manifest().createdAt(),finished,Files.size(archive),hash(archive)),
                                new Attempt(runId,started,finished,"VERIFIED","OK"));
                        }
                    } catch(Exception ex) {
                        failed=true;state=new State(state.format(),state.targetId(),state.lastVerified(),new Attempt(runId,started,clock.instant().toString(),"FAILED","BACKUP_FAILED"));
                    } finally {if(password!=null)Arrays.fill(password,'\0');}
                    save(root,state);
                }
                var report=report(root,engine,data,env,state,snapshot,invalidUsage,clock.instant(),runId);
                atomic(root.resolve("latest.json"),OperationsUsage.JSON.writeValueAsBytes(report));
                atomic(root.resolve("latest.html"),OperationsReport.html(report).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.println(failed?"ERRO: backup não concluído; a última cópia válida foi preservada.":"OK: rotina concluída; "+(action.equals("backup")?"backup criado e verificado.":"nenhuma nova cópia criada."));
                output.println("Relatório: "+root.resolve("latest.html"));
                output.println("Cotas não informadas exigem conferência nos painéis. Nenhum recurso pago ou agendamento foi criado.");
                return failed?1:report.status().equals("OK")?0:2;
            }
        } catch(Exception ex) {
            output.println("ERRO: operação não concluída. Confira opções, origem do banco, pasta exclusiva e permissões. Dados privados não foram exibidos.");
            return 1;
        }
    }
}
