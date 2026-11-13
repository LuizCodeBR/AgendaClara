package br.com.luizcode.agenda.backup;

import java.util.*;

public final class OperationsReport {
    private OperationsReport() {}
    public record Verified(String filename,String engine,String createdAt,String verifiedAt,long bytes,String sha256) {}
    public record Attempt(String id,String startedAt,String finishedAt,String result,String code) {}
    public record State(int format,String targetId,Verified lastVerified,Attempt lastAttempt) {
        public State(int format,Verified lastVerified,Attempt lastAttempt){this(format,null,lastVerified,lastAttempt);}
    }
    public record Backup(String status,Verified lastVerified,Attempt lastAttempt,boolean fileMatches,boolean sourceBound,long count,long bytes) {}
    public record Report(int format,String version,String generatedAt,String runId,boolean onDemandOnly,String engine,String status,
                         Backup backup,Long databaseBytes,OperationsUsage.Observation provider,List<OperationsUsage.Metric> metrics,List<String> notices) {}

    static String escape(String value) {
        return value==null?"—":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    static String number(Double value,String unit) {
        if(value==null)return "Não informado";
        if(unit.equals("bytes"))return String.format(Locale.ROOT,"%.2f MiB",value/1048576);
        return String.format(Locale.ROOT,"%.2f %s",value,unit);
    }
    static String label(String status) {
        return switch(status) {
            case "CURRENT"->"Observação recente";
            case "OK","VERIFIED"->"Conferido";
            case "WARNING"->"Atenção: 70% ou mais";
            case "CRITICAL"->"Atenção crítica: 90% ou mais";
            case "EXCEEDED"->"Limite atingido";
            case "STALE"->"Observação antiga / período encerrado";
            case "UNKNOWN"->"Não informado";
            case "FAILED"->"Falha";
            case "MISMATCH"->"Arquivo ausente ou alterado";
            case "UNBOUND"->"Origem anterior não vinculada";
            case "INVALID"->"Observação inválida ou de outro recurso";
            case "INTERRUPTED"->"Tentativa interrompida";
            case "RUNNING"->"Em execução";
            case "NONE"->"Nenhum backup verificado";
            default->"Precisa de atenção";
        };
    }
    static String html(Report report) {
        var out=new StringBuilder("""
            <!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
            <title>Agenda Clara · operação</title><style>
            *{box-sizing:border-box}body{margin:0;background:#f4f7f5;color:#183b32;font:16px/1.55 system-ui,sans-serif}
            main{max-width:1100px;margin:auto;padding:32px 20px}h1{font-size:clamp(1.8rem,5vw,2.8rem);line-height:1.15}
            .card{background:white;border:1px solid #cedfd6;border-radius:16px;padding:22px;margin:18px 0;overflow-wrap:anywhere}
            .tag{display:inline-block;border-radius:99px;padding:4px 12px;background:#e5f0ea;font-weight:650}
            .attention{background:#fff0d5;color:#6e4600}dl{display:grid;grid-template-columns:180px 1fr;gap:8px}dd{margin:0}
            table{border-collapse:collapse;width:100%;text-align:left}td,th{padding:12px 8px;border-bottom:1px solid #e1e9e4;vertical-align:top}
            .scroll{overflow-x:auto}small{color:#4e685e}code{font-size:.9rem}a{color:#195e47}
            @media(max-width:600px){main{padding:20px 12px}.card{padding:16px}dl{grid-template-columns:1fr;gap:0}dd{margin-bottom:12px}table{min-width:650px}}
            </style><main><p>AGENDA CLARA · FERRAMENTAS DO RESPONSÁVEL PELA INSTALAÇÃO</p><h1>Backups e limites gratuitos</h1>
            """);
        out.append("<span class=\"tag ").append(report.status().equals("OK")?"":"attention").append("\">")
            .append(escape(label(report.status()))).append("</span><p>Relatório sob demanda. Sem agendamento, envio externo ou atualização automática.</p>");
        out.append("<div class=\"card\"><dl><dt>Gerado em UTC</dt><dd>").append(escape(report.generatedAt()))
            .append("</dd><dt>Versão</dt><dd>").append(escape(report.version())).append("</dd><dt>Banco</dt><dd>")
            .append(escape(report.engine())).append("</dd><dt>Identificador</dt><dd>").append(escape(report.runId())).append("</dd></dl></div>");
        var b=report.backup();out.append("<section class=\"card\"><h2>Cópia de segurança</h2><p>").append(escape(label(b.status()))).append("</p>");
        if(b.lastVerified()!=null) {
            var v=b.lastVerified();out.append("<dl><dt>Arquivo</dt><dd>").append(escape(v.filename())).append("</dd><dt>Verificado em UTC</dt><dd>")
                .append(escape(v.verifiedAt())).append("</dd><dt>SHA-256</dt><dd><code>").append(escape(v.sha256())).append("</code></dd></dl>");
        }
        out.append("<p>Origem: ").append(b.sourceBound()?"vinculada à configuração desta execução":"histórico anterior ainda não vinculado").append(".</p>");
        if(b.lastAttempt()!=null)out.append("<p>Última tentativa: ").append(escape(label(b.lastAttempt().result()))).append(" · ").append(escape(b.lastAttempt().code())).append("</p>");
        out.append("<p>").append(b.count()).append(" cópia(s) nesta pasta, ").append(number((double)b.bytes(),"bytes"))
            .append(". Nenhuma cópia foi excluída.</p><small>A validação criptográfica ocorreu na criação. O relatório confere o hash do último arquivo; não substitui um ensaio de restauração.</small></section>");
        out.append("<section class=\"card\"><h2>Uso e cotas</h2><p>Alertas em 70%, 90% e 100%. Valores não informados não representam consumo zero.</p><div class=\"scroll\"><table>");
        out.append("<caption>Observação dos provedores: ").append(escape(report.provider().observedAt())).append(" · ").append(escape(label(report.provider().status()))).append("</caption>");
        out.append("<thead><tr><th>Recurso</th><th>Uso / referência</th><th>Estado</th><th>Origem</th></tr></thead><tbody>");
        for(var metric:report.metrics())out.append("<tr><td>").append(escape(metric.label())).append("</td><td>")
            .append(escape(number(metric.used(),metric.unit()))).append(" / ").append(escape(number(metric.limit(),metric.unit())))
            .append(metric.percentage()==null?"":" · "+metric.percentage()+"%").append("</td><td>").append(escape(label(metric.status())+(metric.freshness().equals("STALE")?" · observação antiga":"")))
            .append("</td><td>").append(escape(metric.source())).append("</td></tr>");
        out.append("</tbody></table></div></section><section class=\"card\"><h2>O que considerar</h2><ul>");
        for(String notice:report.notices())out.append("<li>").append(escape(notice)).append("</li>");
        out.append("</ul><p>Confira os totais oficiais no <a href=\"https://console.neon.tech/\">Neon</a> e no <a href=\"https://dashboard.render.com/billing#included-usage\">Render</a>.</p></section></main></html>");
        return out.toString();
    }
}
