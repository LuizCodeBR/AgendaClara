package br.com.luizcode.agenda;

import static org.assertj.core.api.Assertions.*;
import br.com.luizcode.agenda.reports.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportCsvReviewTest {
    private List<List<String>> parse(String csv){
        var rows=new ArrayList<List<String>>();var row=new ArrayList<String>();var cell=new StringBuilder();boolean quoted=false;
        for(int i=1;i<csv.length();i++){
            char c=csv.charAt(i);
            if(c=='"'){if(quoted&&i+1<csv.length()&&csv.charAt(i+1)=='"'){cell.append('"');i++;}else quoted=!quoted;}
            else if(c==';'&&!quoted){row.add(cell.toString());cell.setLength(0);}
            else if(c=='\r'&&!quoted){assertThat(csv.charAt(++i)).isEqualTo('\n');row.add(cell.toString());rows.add(List.copyOf(row));row.clear();cell.setLength(0);}
            else cell.append(c);
        }
        assertThat(quoted).isFalse();assertThat(cell).isEmpty();return rows;
    }
    @ParameterizedTest @ValueSource(strings={"=1+1","+SUM(1)","-1+1","@SUM(1)","\t=1+1","\r\n=1+1","\";=1+1","＝1+1","＋1+1","－1+1","＠SUM(1)","\u0085=1+1","\u202e=1+1"})
    void untrustedLabelsRemainSingleTextCellsInEveryExportedPosition(String payload){
        var metrics=new ReportService.Metrics(1,1,0,0,0,0,1999);var professional=UUID.randomUUID();var service=UUID.randomUUID();
        var date=LocalDate.of(2030,6,4);
        var report=new ReportService.Report(date,date,"America/Sao_Paulo",payload,professional,service,payload,payload,Instant.parse("2030-06-04T14:00:00Z"),metrics,
            List.of(new ReportService.Professional(professional,payload,true,metrics)),
            List.of(new ReportService.Row(professional,payload,true,service,payload,metrics)),List.of(new ReportService.Day(date,metrics)));
        var csv=new String(ReportCsv.write(report),StandardCharsets.UTF_8);assertThat(csv).startsWith("\ufeff");
        var rows=parse(csv);assertThat(rows).hasSize(3);rows.forEach(row->assertThat(row).hasSize(20));
        for(var row:rows.subList(1,3)){
            assertThat(row.get(1)).startsWith("Empresa: ");assertThat(row.get(6)).startsWith("Profissional: ");assertThat(row.get(7)).startsWith("Serviço: ");
            for(String value:row)assertThat(value.codePoints().anyMatch(c->Character.getType(c)==Character.CONTROL||Character.getType(c)==Character.FORMAT)).isFalse();
            assertThat(row.get(19)).isEqualTo("19,99");
        }
        assertThat(rows.get(2).get(9)).startsWith("Profissional: ");assertThat(rows.get(2).get(12)).startsWith("Serviço: ");
    }
}
