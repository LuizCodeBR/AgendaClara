package br.com.luizcode.agenda.backup;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** One validated configuration drives JDBC inspection and the native export/restore. */
final class BackupPostgres {
    private BackupPostgres() {}
    static final long RESTORE_LOCK=0x4147454e4441424bL;
    private static final Set<String> PARAMETERS=Set.of("sslmode","channelBinding","sslrootcert","connectTimeout","socketTimeout","loginTimeout","cancelSignalTimeout","ApplicationName","tcpKeepAlive","queryTimeout");
    static Map<String,String> parameters(DatabaseSnapshot.Pg pg) throws IOException {
        URI uri=URI.create(pg.url().substring(5));var result=new LinkedHashMap<String,String>();
        if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&",-1)) {
            String[] pair=part.split("=",2);
            String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);
            if(pair.length!=2||!PARAMETERS.contains(key)||result.containsKey(key))throw new IOException("Parâmetro PostgreSQL não suportado ou repetido.");
            result.put(key,URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }
        boolean local=Set.of("localhost","127.0.0.1","[::1]","::1").contains(uri.getHost());
        String ssl=result.getOrDefault("sslmode",local?"disable":"require"),binding=result.getOrDefault("channelBinding","prefer");
        if(!Set.of("disable","require","verify-ca","verify-full").contains(ssl)||(!local&&ssl.equals("disable")))
            throw new IOException("Use TLS para conexões PostgreSQL externas.");
        if(!Set.of("disable","prefer","require").contains(binding))throw new IOException("Vínculo TLS inválido.");
        result.put("sslmode",ssl);result.put("channelBinding",binding);
        // pgJDBC gives URL properties priority. Rebuild the URL rather than relying on a weaker Properties override.
        result.put("connectTimeout","10");result.put("loginTimeout","15");result.put("socketTimeout","20");
        result.put("cancelSignalTimeout","10");result.put("queryTimeout","20");
        return result;
    }
    static String jdbcUrl(DatabaseSnapshot.Pg pg,Map<String,String> env) throws IOException {
        var parameters=parameters(pg);
        String root=env.getOrDefault("PGSSLROOTCERT","");
        if(!parameters.containsKey("sslrootcert")&&!root.isBlank())parameters.put("sslrootcert",root);
        var query=new StringJoiner("&");
        parameters.forEach((key,value)->query.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
        return pg.url().split("\\?",2)[0]+"?"+query;
    }
    static Connection connect(DatabaseSnapshot.Pg pg,Map<String,String> env) throws SQLException,IOException {
        return DriverManager.getConnection(jdbcUrl(pg,env),pg.user(),pg.password());
    }
    static String database(DatabaseSnapshot.Pg pg) {
        return URI.create(pg.url().substring(5)).getPath().substring(1);
    }
    static String databaseArgument(DatabaseSnapshot.Pg pg) {
        return "dbname='"+database(pg).replace("\\","\\\\").replace("'","\\'")+"'";
    }
    static Map<String,String> environment(DatabaseSnapshot.Pg pg,Map<String,String> supplied) throws IOException {
        var result=new HashMap<String,String>();
        for(String name:List.of("PATH","SystemRoot","WINDIR","COMSPEC","PATHEXT","TEMP","TMP","TMPDIR","HOME","USERPROFILE","APPDATA","LOCALAPPDATA",
                "LANG","LC_ALL","LC_CTYPE","DOCKER_HOST","DOCKER_CONTEXT","DOCKER_CONFIG","DOCKER_TLS_VERIFY","DOCKER_CERT_PATH","SSH_AUTH_SOCK")) {
            String value=supplied.containsKey(name)?supplied.get(name):System.getenv(name);
            if(value!=null)result.put(name,value);
        }
        URI uri=URI.create(pg.url().substring(5));var parameters=parameters(pg);
        String host=uri.getHost();if(host.startsWith("[")&&host.endsWith("]"))host=host.substring(1,host.length()-1);
        result.put("PGHOST",host);result.put("PGPORT",Integer.toString(uri.getPort()<0?5432:uri.getPort()));
        result.put("PGDATABASE",database(pg));result.put("PGUSER",pg.user());result.put("PGPASSWORD",pg.password());
        result.put("PGCONNECT_TIMEOUT","10");result.put("PGSSLMODE",parameters.get("sslmode"));result.put("PGCHANNELBINDING",parameters.get("channelBinding"));
        String root=parameters.getOrDefault("sslrootcert",supplied.getOrDefault("PGSSLROOTCERT",""));if(!root.isBlank())result.put("PGSSLROOTCERT",root);
        return result;
    }
    static void restoreLock(Connection connection) throws Exception {
        try(var statement=connection.prepareStatement("select pg_catalog.pg_try_advisory_lock(?)")) {
            statement.setLong(1,RESTORE_LOCK);statement.setQueryTimeout(10);
            try(var rows=statement.executeQuery()) {
                if(!rows.next()||!rows.getBoolean(1))throw new IOException("Outra restauração está em andamento neste banco.");
            }
        }
    }
}
