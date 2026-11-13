package br.com.luizcode.agenda.backup;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class BackupTool {
    private BackupTool() {}
    public static boolean requested(String[] args){return Arrays.stream(args).anyMatch(arg->arg.startsWith("--backup"));}
    public static int run(String[] args,Map<String,String> env,PrintStream output) {
        char[] password=null;
        try {
            var options=new HashMap<String,String>();
            for(String arg:args) {
                int equals=arg.indexOf('=');
                if(!arg.startsWith("--")||equals<3)throw new IOException("Use opções no formato --nome=valor.");
                String key=arg.substring(2,equals);
                if(!Set.of("backup","data-dir","output","input","target-dir").contains(key)||options.put(key,arg.substring(equals+1))!=null)
                    throw new IOException("Opção desconhecida ou repetida.");
            }
            String action=options.getOrDefault("backup","help");
            if(action.equals("help")) {
                output.println("Agenda Clara: --backup=create|verify|restore|create-postgres|restore-postgres.\n"+
                        "create: --data-dir=PASTA --output=ARQUIVO.acbackup (opcionais; padrão data e backups).\n"+
                        "verify: --input=ARQUIVO.acbackup. restore: --input=ARQUIVO --target-dir=NOVA-PASTA.\n"+
                        "PostgreSQL usa DB_URL, DB_USER, DB_PASSWORD e a chave original no ambiente privado.\n"+
                        "A senha é solicitada no terminal, ou lida de AGENDA_BACKUP_PASSWORD. Não use senha na linha de comando.");
                return 0;
            }
            if(!Set.of("create","verify","restore","create-postgres","restore-postgres").contains(action))throw new IOException("Operação de backup inválida.");
            Path data=Path.of(options.getOrDefault("data-dir",env.getOrDefault("AGENDA_DATA_DIR","data")));
            if(env.containsKey("AGENDA_BACKUP_PASSWORD"))password=env.get("AGENDA_BACKUP_PASSWORD").toCharArray();
            else {
                Console console=System.console();
                if(console==null)throw new IOException("Abra um terminal interativo ou configure AGENDA_BACKUP_PASSWORD no ambiente privado.");
                password=console.readPassword("Senha exclusiva do backup: ");
                if(password==null)throw new IOException("Operação cancelada.");
                if(action.startsWith("create")) {
                    char[] confirmation=console.readPassword("Confirme a senha do backup: ");
                    try {if(!Arrays.equals(password,confirmation))throw new IOException("As senhas não coincidem.");}
                    finally {if(confirmation!=null)Arrays.fill(confirmation,'\0');}
                }
            }
            if(action.startsWith("create")) {
                Path destination=Path.of(options.getOrDefault("output","backups/AgendaClara-"+DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())+"-"+UUID.randomUUID()+".acbackup"));
                if(action.equals("create"))DatabaseSnapshot.createLocal(data,destination,password,env);
                else DatabaseSnapshot.createPostgres(data,destination,password,env);
                output.println("OK: backup criptografado criado em "+destination.toAbsolutePath().normalize());
            } else {
                if(!options.containsKey("input"))throw new IOException("Informe --input.");
                try(var bundle=BackupArchive.read(Path.of(options.get("input")),password)) {
                    if(action.equals("verify"))output.println("OK: senha, autenticação e integridade do backup verificadas. Banco: "+bundle.manifest().engine()+". Teste a restauração em um destino separado.");
                    else {
                        if(!options.containsKey("target-dir"))throw new IOException("Informe --target-dir com uma pasta que ainda não exista.");
                        Path target=Path.of(options.get("target-dir"));
                        if(action.equals("restore"))DatabaseSnapshot.restoreLocal(bundle,target);
                        else DatabaseSnapshot.restorePostgres(bundle,target,env);
                        output.println("OK: restauração concluída. Arquivos preservados em "+target.toAbsolutePath().normalize());
                    }
                }
            }
            return 0;
        } catch(Exception ex) {
            // Database, native-tool and cryptographic errors may contain private configuration.
            String detail=(ex instanceof IOException && !ex.getClass().getName().startsWith("java.nio.file."))?ex.getMessage():
                    "Não foi possível concluir. Confira a senha, a integridade, as permissões e a configuração; nenhum destino existente foi substituído.";
            output.println("ERRO: "+detail);return 1;
        } finally {if(password!=null)Arrays.fill(password,'\0');}
    }
}
