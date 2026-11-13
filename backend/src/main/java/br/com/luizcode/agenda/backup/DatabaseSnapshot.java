package br.com.luizcode.agenda.backup;

import java.io.*;
import java.net.URI;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static br.com.luizcode.agenda.backup.BackupArchive.*;

public final class DatabaseSnapshot {
    public record Info(String engineVersion,String schema,String keyId) {}
    public record Pg(String url,String user,String password) {
        @Override public String toString(){return "Pg[protected]";}
    }
    public static Info inspect(Connection connection,byte[] key) throws Exception {
        String schema;
        try(var statement=connection.createStatement();var rows=statement.executeQuery("select \"version\" from \"flyway_schema_history\" where \"success\"=true order by \"installed_rank\" desc limit 1")) {
            if(!rows.next())throw new IOException("O banco não contém as migrações da Agenda Clara.");
            schema=rows.getString(1);
        }
        if(!"6".equals(schema))throw new IOException("Versão de esquema incompatível com esta ferramenta.");
        String expected=null;
        try(var statement=connection.createStatement();var rows=statement.executeQuery("select key_id from mail_key_identity where id=1")){if(rows.next())expected=rows.getString(1);}
        String actual=null;
        if(key!=null) {
            byte[] raw=Base64.getDecoder().decode(new String(key,java.nio.charset.StandardCharsets.US_ASCII).strip());
            try{if(raw.length!=32)throw new IOException("Formato da chave inválido.");actual=digest(raw);}finally{Arrays.fill(raw,(byte)0);}
        }
        if(expected!=null&&!expected.equals(actual))throw new IOException("A chave original do banco está ausente ou é diferente.");
        try(var statement=connection.createStatement();var rows=statement.executeQuery("select distinct key_id from mail_outbox where payload is not null")) {
            while(rows.next())if(!Objects.equals(actual,rows.getString(1)))throw new IOException("A chave não corresponde ao conteúdo da fila.");
        }
        return new Info(connection.getMetaData().getDatabaseProductVersion(),schema,actual);
    }
    static byte[] key(Path data,Map<String,String> env) throws Exception {
        String configured=env.getOrDefault("MAIL_ENCRYPTION_KEY","");
        if(!configured.isBlank())return configured.strip().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Path path=Path.of(env.getOrDefault("MAIL_KEY_FILE",data.resolve("mail-outbox.key").toString()));
        if(!Files.exists(path))return null;
        if(Files.size(path)>256)throw new IOException("Formato da chave inválido.");
        return Files.readAllBytes(path);
    }
    static String h2(Path data) throws IOException {
        String location=data.resolve("agenda").toAbsolutePath().normalize().toString();
        if(location.contains(";")||location.contains("\n")||location.contains("\r"))throw new IOException("Use uma pasta sem ponto e vírgula ou quebras de linha.");
        return "jdbc:h2:file:"+location+";IFEXISTS=TRUE;ACCESS_MODE_DATA=r";
    }
    public static void createLocal(Path data,Path output,char[] password,Map<String,String> env) throws Exception {
        Path absolute=data.toAbsolutePath().normalize(),database=absolute.resolve("agenda.mv.db");
        if(!Files.isRegularFile(database,LinkOption.NOFOLLOW_LINKS))throw new IOException("Banco local não encontrado.");
        Path stage=staging();
        var files=new LinkedHashMap<String,byte[]>();
        try(var guard=FileChannel.open(absolute.resolve("mail-outbox.key.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var keyLock=guard.tryLock()) {
            if(keyLock==null)throw new IOException("A chave está sendo utilizada. Encerre as instâncias locais.");
            try(var channel=FileChannel.open(database,StandardOpenOption.READ,StandardOpenOption.WRITE);
                    var databaseLock=channel.tryLock()) {
                if(databaseLock==null)throw new IOException("Encerre o servidor local antes de criar o backup.");
                if(channel.size()>LIMIT)throw new IOException("O banco excede o limite de 64 MiB.");
                Path copy=privateFile(stage.resolve("agenda.mv.db"));
                try(var target=Files.newOutputStream(copy)) { channel.position(0); Channels.newInputStream(channel).transferTo(target); }
                byte[] key=key(absolute,env);
                if(key!=null)files.put("mail-outbox.key",key);
            }
            try(var connection=DriverManager.getConnection(h2(stage),"sa","")) {
                Info info=inspect(connection,files.get("mail-outbox.key"));
                connection.close(); // H2 must be closed before reading its physical snapshot.
                files.put("agenda.mv.db",readBounded(stage.resolve("agenda.mv.db")));
                write(output,"h2",info.engineVersion(),info.schema(),info.keyId(),files,password);
            }
        } catch(OverlappingFileLockException ex) { throw new IOException("Encerre o servidor local antes de criar o backup."); }
        finally { files.values().forEach(value->Arrays.fill(value,(byte)0)); clearStage(stage); }
    }
    public static void restoreLocal(Bundle bundle,Path target) throws Exception {
        if(!bundle.manifest().engine().equals("h2"))throw new IOException("Este backup exige restauração PostgreSQL.");
        Path stage=staging();
        try {
            materialize(bundle,stage);
            try(var connection=DriverManager.getConnection(h2(stage),"sa","")) {
                Info info=inspect(connection,bundle.files().get("mail-outbox.key"));
                if(!info.engineVersion().equals(bundle.manifest().engineVersion()))throw new IOException("Versão do H2 incompatível.");
            }
            publish(bundle,target);
        } finally {clearStage(stage);}
    }
    static void materialize(Bundle bundle,Path directory) throws IOException {
        for(var entry:bundle.files().entrySet()) {
            Path file=privateFile(directory.resolve(entry.getKey())); Files.write(file,entry.getValue());
        }
    }
    static void publish(Bundle bundle,Path target) throws IOException {
        Path absolute=target.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Files.createDirectory(absolute); protect(absolute,true);
        var created=new ArrayList<Path>();
        try {
            for(var entry:bundle.files().entrySet()) {
                if(entry.getKey().equals("database.dump"))continue;
                Path file=privateFile(absolute.resolve(entry.getKey()));created.add(file);Files.write(file,entry.getValue());
            }
        } catch(Exception ex) {
            for(Path path:created)if(path.getParent().equals(absolute))Files.deleteIfExists(path);
            try{Files.delete(absolute);}catch(IOException ignored){}
            throw ex;
        }
    }
    public static Pg pg(Map<String,String> env) throws IOException {
        String url=env.getOrDefault("DB_URL",""),user=env.getOrDefault("DB_USER",""),password=env.getOrDefault("DB_PASSWORD","");
        if(!url.startsWith("jdbc:postgresql://")||user.isBlank())throw new IOException("Configure DB_URL JDBC PostgreSQL, DB_USER e DB_PASSWORD no ambiente privado.");
        URI uri=URI.create(url.substring(5));
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPath()==null||uri.getPath().length()<2)
            throw new IOException("Endereço PostgreSQL inválido.");
        return new Pg(url,user,password);
    }
    static void nativeTool(String tool,Pg pg,Map<String,String> env,Path input,Path output) throws Exception {
        URI uri=URI.create(pg.url().substring(5));
        var command=new ArrayList<String>();String container=env.getOrDefault("AGENDA_PG_CONTAINER","");
        var names=List.of("PGHOST","PGPORT","PGDATABASE","PGUSER","PGPASSWORD","PGSSLMODE","PGSSLROOTCERT","PGCONNECT_TIMEOUT");
        if(!container.isBlank()) {
            command.addAll(List.of("docker","exec","-i"));
            for(String name:names)command.addAll(List.of("--env",name));
            command.add(container);
        }
        command.add(tool.equals("pg_dump")?env.getOrDefault("PG_DUMP_PATH","pg_dump"):env.getOrDefault("PG_RESTORE_PATH","pg_restore"));
        command.add("--no-password");
        if(tool.equals("pg_dump"))command.addAll(List.of("--format=custom","--no-owner","--no-privileges"));
        else command.addAll(List.of("--exit-on-error","--single-transaction","--no-owner","--no-privileges","--dbname",uri.getPath().substring(1)));
        ProcessBuilder builder=new ProcessBuilder(command);
        var processEnv=builder.environment();
        processEnv.put("PGHOST",uri.getHost());processEnv.put("PGPORT",String.valueOf(uri.getPort()<0?5432:uri.getPort()));
        processEnv.put("PGDATABASE",uri.getPath().substring(1));processEnv.put("PGUSER",pg.user());processEnv.put("PGPASSWORD",pg.password());
        processEnv.put("PGCONNECT_TIMEOUT","10");processEnv.put("PGSSLROOTCERT",env.getOrDefault("PGSSLROOTCERT",""));
        boolean local=Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost());
        String ssl=local?"disable":"require";
        if(uri.getRawQuery()!=null)for(String parameter:uri.getRawQuery().split("&")) {
            String[] pair=parameter.split("=",2);
            if(pair.length==2&&pair[0].equals("sslmode"))ssl=pair[1];
        }
        if(!Set.of("disable","require","verify-ca","verify-full").contains(ssl))throw new IOException("Modo TLS PostgreSQL não suportado.");
        processEnv.put("PGSSLMODE",ssl);
        if(input!=null)builder.redirectInput(input.toFile());
        if(output==null)builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process=builder.start();
        if(input==null)process.getOutputStream().close();
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Future<?> copy=output==null?null:executor.submit(()->{
                try(var source=process.getInputStream();var destination=Files.newOutputStream(output)) {
                    byte[] buffer=new byte[8192];long total=0;int count;
                    while((count=source.read(buffer))!=-1) {
                        total+=count;if(total>LIMIT)throw new IOException("O export excede o limite de 64 MiB.");
                        destination.write(buffer,0,count);
                    }
                } catch(IOException ex) {process.destroyForcibly();throw new java.io.UncheckedIOException(ex);}
            });
            try {
                if(!process.waitFor(120,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("A ferramenta PostgreSQL excedeu dois minutos.");}
                if(copy!=null)copy.get(10,TimeUnit.SECONDS);
                if(process.exitValue()!=0)throw new IOException("A ferramenta PostgreSQL falhou. Confira versão do cliente, acesso, TLS e permissões; credenciais não foram exibidas.");
            } finally {if(process.isAlive())process.destroyForcibly();}
        }
    }
    public static void createPostgres(Path data,Path output,char[] password,Map<String,String> env) throws Exception {
        Pg pg=pg(env);Path stage=staging();var files=new LinkedHashMap<String,byte[]>();
        try {
            byte[] key=key(data,env);if(key!=null)files.put("mail-outbox.key",key);
            Info info;
            try(var connection=DriverManager.getConnection(pg.url(),pg.user(),pg.password())){info=inspect(connection,key);}
            Path dump=privateFile(stage.resolve("database.dump"));nativeTool("pg_dump",pg,env,null,dump);
            // The installation key is immutable: recheck after the consistent native dump.
            try(var connection=DriverManager.getConnection(pg.url(),pg.user(),pg.password())) {
                if(!Objects.equals(info.keyId(),inspect(connection,key).keyId()))throw new IOException("A identidade da chave mudou durante o backup.");
            }
            files.put("database.dump",readBounded(dump));write(output,"postgres",info.engineVersion(),info.schema(),info.keyId(),files,password);
        } finally {files.values().forEach(value->Arrays.fill(value,(byte)0));clearStage(stage);}
    }
    public static void restorePostgres(Bundle bundle,Path keyTarget,Map<String,String> env) throws Exception {
        if(!bundle.manifest().engine().equals("postgres"))throw new IOException("Este backup exige restauração local H2.");
        if(Files.exists(keyTarget,LinkOption.NOFOLLOW_LINKS))throw new IOException("Escolha uma nova pasta para a chave restaurada.");
        Pg pg=pg(env);
        try(var connection=DriverManager.getConnection(pg.url(),pg.user(),pg.password());
                var statement=connection.createStatement();var rows=statement.executeQuery("""
                select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
                where left(n.nspname,3)<>'pg_' and n.nspname<>'information_schema'
                """)) {
            rows.next();if(rows.getLong(1)!=0)throw new IOException("A restauração exige um banco PostgreSQL vazio e exclusivo; nenhum dado foi apagado.");
        }
        Path stage=staging();
        try {
            Path dump=privateFile(stage.resolve("database.dump"));Files.write(dump,bundle.files().get("database.dump"));
            nativeTool("pg_restore",pg,env,dump,null);
            try(var connection=DriverManager.getConnection(pg.url(),pg.user(),pg.password())){inspect(connection,bundle.files().get("mail-outbox.key"));}
            publish(bundle,keyTarget);
        } finally {clearStage(stage);}
    }
}
