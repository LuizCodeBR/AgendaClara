package br.com.luizcode.agenda.reports;

import br.com.luizcode.agenda.identity.AgendaUser;
import br.com.luizcode.agenda.shared.BusinessException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {
    private static final String COMPANY_HEADER="X-Agenda-Business";
    private final ReportService reports;
    public ReportController(ReportService reports){this.reports=reports;}
    // The header is a consistency precondition, never the source of authorization.
    private static UUID business(AgendaUser user,UUID expected){
        if(expected!=null&&!expected.equals(user.businessId()))
            throw new BusinessException(HttpStatus.PRECONDITION_FAILED,"A conta ativa mudou. Entre novamente para consultar a empresa correta.");
        return user.businessId();
    }
    @GetMapping("/options")
    ReportService.Options options(@AuthenticationPrincipal AgendaUser user,
        @RequestHeader(value=COMPANY_HEADER,required=false) UUID expectedBusiness){
        return reports.options(business(user,expectedBusiness));
    }
    @GetMapping
    ReportService.Report read(@AuthenticationPrincipal AgendaUser user,
        @RequestHeader(value=COMPANY_HEADER,required=false) UUID expectedBusiness,
        @RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) UUID professionalId,@RequestParam(required=false) UUID serviceId){
        return reports.read(business(user,expectedBusiness),from,to,professionalId,serviceId);
    }
    @GetMapping(value="/export",produces="text/csv;charset=UTF-8")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal AgendaUser user,
        @RequestHeader(value=COMPANY_HEADER,required=false) UUID expectedBusiness,
        @RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) UUID professionalId,@RequestParam(required=false) UUID serviceId){
        var report=reports.read(business(user,expectedBusiness),from,to,professionalId,serviceId);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"AgendaClara-relatorio-"+from+"-a-"+to+".csv\"")
            .cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(ReportCsv.write(report));
    }
    @ExceptionHandler({org.springframework.dao.QueryTimeoutException.class,org.springframework.transaction.TransactionTimedOutException.class})
    ResponseEntity<ProblemDetail> timeout(Exception ignored){
        var problem=ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "A consulta demorou mais que o permitido. Reduza o período ou use os filtros e tente novamente.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
