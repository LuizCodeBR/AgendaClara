package br.com.luizcode.agenda.reports;

import br.com.luizcode.agenda.identity.AgendaUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {
    private final ReportService reports;
    public ReportController(ReportService reports){this.reports=reports;}
    @GetMapping("/options")
    ReportService.Options options(@AuthenticationPrincipal AgendaUser user){return reports.options(user.businessId());}
    @GetMapping
    ReportService.Report read(@AuthenticationPrincipal AgendaUser user,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) UUID professionalId,@RequestParam(required=false) UUID serviceId){
        return reports.read(user.businessId(),from,to,professionalId,serviceId);
    }
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal AgendaUser user,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) UUID professionalId,@RequestParam(required=false) UUID serviceId){
        var report=reports.read(user.businessId(),from,to,professionalId,serviceId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"AgendaClara-relatorio-"+from+"-a-"+to+".csv\"")
            .cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(ReportCsv.write(report));
    }
}