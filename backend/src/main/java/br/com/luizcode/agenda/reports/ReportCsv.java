package br.com.luizcode.agenda.reports;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ReportCsv {
    private ReportCsv(){}
    private static void row(StringBuilder out,List<String> cells){out.append(cells.stream().map(s->"\""+s.replace("\"","\"\"")+"\"").collect(java.util.stream.Collectors.joining(";"))).append("\r\n");}
    private static void metrics(List<String> cells,ReportService.Metrics m){
        for(long n:new long[]{m.total(),m.completed(),m.noShow(),m.cancelled(),m.pending(),m.upcoming()})cells.add(Long.toString(n));
        cells.add(BigDecimal.valueOf(m.completedValueCents(),2).toPlainString().replace('.',','));
    }
    public static byte[] write(ReportService.Report report){
        var out=new StringBuilder("\ufeff");
        row(out,List.of("Tipo","Empresa","Inicio","Fim","Fuso","Consultado em UTC","Filtro profissional","Filtro servico","Profissional ID","Profissional","Ativo","Servico ID","Servico na reserva","Total","Concluidos","Faltas","Cancelados","Resultado pendente","Confirmados sem termino","Valor servicos concluidos BRL"));
        var base=List.of("Empresa: "+ReportService.line(report.businessName()),report.from().toString(),report.to().toString(),report.zoneId(),report.generatedAt().toString(),"Profissional: "+ReportService.line(report.professionalLabel()),"Serviço: "+ReportService.line(report.serviceLabel()));
        var total=new ArrayList<String>();total.add("TOTAL");total.addAll(base);total.addAll(List.of("","","","",""));metrics(total,report.totals());row(out,total);
        for(var item:report.rows()){
            var cells=new ArrayList<String>();cells.add("PROFISSIONAL_SERVICO");cells.addAll(base);
            // Fixed visible prefixes keep user-controlled labels textual even after CSV re-saving.
            cells.addAll(List.of(item.professionalId().toString(),"Profissional: "+ReportService.line(item.professionalName()),item.active()?"Sim":"Não",item.serviceId().toString(),"Serviço: "+ReportService.line(item.serviceName())));
            metrics(cells,item.metrics());row(out,cells);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}