package br.com.luizcode.agenda.backup;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Operator-supplied observations, never a credential file or public API input. */
public final class OperationsUsage {
    private OperationsUsage() {}
    static final ObjectMapper JSON=tools.jackson.databind.json.JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    static final long INPUT_LIMIT=16384;
    public record Neon(String projectId,String plan,Double computeCuHours,Double transferBytes,Double storageBytes,String periodStart,String periodEnd) {}
    public record Render(String workspaceId,String plan,Double freeInstanceHours,Double outboundBytes,Double pipelineMinutes,Double pipelineLimit,String periodStart,String periodEnd) {}
    public record Snapshot(int format,String observedAt,String source,Neon neon,Render render) {}
    public record Metric(String id,String label,Double used,Double limit,String unit,String source,String status,Double percentage,String freshness) {}
    public record Observation(String observedAt,String source,String status,Snapshot snapshot) {}

    static Snapshot read(Path path,Map<String,String> env,Instant now) throws Exception {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>INPUT_LIMIT)
            throw new IOException("Observação de consumo ausente, inválida ou maior que 16 KiB.");
        var snapshot=JSON.readValue(OperationsTool.bounded(path,INPUT_LIMIT),Snapshot.class);
        if(snapshot.format()!=1||!Set.of("connector","console").contains(snapshot.source()))
            throw new IOException("Formato ou origem da observação inválidos.");
        Instant observed=Instant.parse(snapshot.observedAt());
        if(observed.isAfter(now.plusSeconds(300)))throw new IOException("A observação está no futuro.");
        if(snapshot.neon()!=null) {
            var n=snapshot.neon();identifier(n.projectId());
            match(env,"NEON_PROJECT_ID",n.projectId());plan(n.plan());
            numbers(n.computeCuHours(),n.transferBytes(),n.storageBytes());period(n.periodStart(),n.periodEnd(),observed);
        }
        if(snapshot.render()!=null) {
            var r=snapshot.render();identifier(r.workspaceId());
            match(env,"RENDER_WORKSPACE_ID",r.workspaceId());plan(r.plan());
            numbers(r.freeInstanceHours(),r.outboundBytes(),r.pipelineMinutes(),r.pipelineLimit());
            if(r.pipelineLimit()!=null&&r.pipelineLimit()<=0)throw new IOException("Limite de compilação inválido.");
            period(r.periodStart(),r.periodEnd(),observed);
        }
        return snapshot;
    }
    static void identifier(String value) throws IOException {
        if(value==null||!value.matches("[a-zA-Z0-9-]{3,100}"))throw new IOException("Identificador inválido.");
    }
    static void match(Map<String,String> env,String key,String value) throws IOException {
        if(env.containsKey(key)&&!env.get(key).equals(value))throw new IOException("A observação pertence a outro recurso.");
    }
    static void plan(String value) throws IOException {
        if(value==null||!Set.of("free","hobby","other").contains(value))throw new IOException("Plano inválido.");
    }
    static void numbers(Double... values) throws IOException {
        for(Double value:values)if(value!=null&&(!Double.isFinite(value)||value<0||value>1e15))
            throw new IOException("Consumo inválido.");
    }
    static void period(String start,String end,Instant observed) throws IOException {
        if(start==null&&end==null)return;
        if(start==null||end==null)throw new IOException("Período incompleto.");
        Instant a=Instant.parse(start),b=Instant.parse(end);
        if(!a.isBefore(b)||observed.isBefore(a)||!observed.isBefore(b))
            throw new IOException("O período não corresponde à observação.");
    }
    static boolean fresh(Snapshot snapshot,Instant now) {
        return snapshot!=null&&!Instant.parse(snapshot.observedAt()).isBefore(now.minus(Duration.ofHours(24)));
    }
    static boolean currentPeriod(String start,String end,Instant now) {
        return start!=null&&end!=null&&!now.isBefore(Instant.parse(start))&&now.isBefore(Instant.parse(end));
    }
    static Metric metric(String id,String label,Double used,Double limit,String unit,String source,boolean fresh) {
        String status=used==null||limit==null?"UNKNOWN":
            used>=limit?"EXCEEDED":used>=limit*.9?"CRITICAL":used>=limit*.7?"WARNING":!fresh?"STALE":"OK";
        Double percentage=used==null||limit==null||limit<=0?null:Math.round(used/limit*1000)/10.0;
        return new Metric(id,label,used,limit,unit,source,status,percentage,used==null?"UNKNOWN":fresh?"CURRENT":"STALE");
    }
    static List<Metric> metrics(Snapshot snapshot,Long databaseBytes,String engine,Instant now) {
        var result=new ArrayList<Metric>();boolean fresh=fresh(snapshot,now);
        var n=snapshot==null?null:snapshot.neon();
        var r=snapshot==null?null:snapshot.render();
        if(engine.equals("postgres")) {
            Double official=n==null?null:n.storageBytes();
            result.add(metric("neon-storage","Neon · armazenamento do projeto",official,1073741824d,"bytes",
                official==null?"não informado":snapshot.source(),fresh));
            result.add(metric("database-size","Banco atual · referência de 1 GiB (estimativa)",
                databaseBytes==null?null:databaseBytes.doubleValue(),1073741824d,"bytes","pg_database_size; não é a cota total do projeto",true));
        } else result.add(metric("h2-size","Banco local · limite da ferramenta de backup",
                databaseBytes==null?null:databaseBytes.doubleValue(),67108864d,"bytes","tamanho do arquivo H2",true));
        boolean neonPeriod=n!=null&&currentPeriod(n.periodStart(),n.periodEnd(),now);
        boolean renderPeriod=r!=null&&currentPeriod(r.periodStart(),r.periodEnd(),now);
        result.add(metric("neon-compute","Neon · processamento mensal",n==null?null:n.computeCuHours(),100d,"CU-h",n==null?"não informado":snapshot.source(),fresh&&neonPeriod));
        result.add(metric("neon-transfer","Neon · transferência pública mensal",n==null?null:n.transferBytes(),5_000_000_000d,"bytes",n==null?"não informado":snapshot.source(),fresh&&neonPeriod));
        result.add(metric("render-hours","Render · horas gratuitas do workspace",r==null?null:r.freeInstanceHours(),750d,"horas",r==null?"não informado":snapshot.source(),fresh&&renderPeriod));
        result.add(metric("render-transfer","Render Hobby · transferência mensal do workspace",r==null?null:r.outboundBytes(),5_000_000_000d,"bytes",r==null?"não informado":snapshot.source(),fresh&&renderPeriod));
        result.add(metric("render-build","Render · minutos de compilação do workspace",r==null?null:r.pipelineMinutes(),r==null?null:r.pipelineLimit(),"minutos",r==null?"não informado":snapshot.source(),fresh&&renderPeriod));
        return List.copyOf(result);
    }
}
