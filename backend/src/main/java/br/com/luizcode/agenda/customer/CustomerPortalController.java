package br.com.luizcode.agenda.customer;

import br.com.luizcode.agenda.customer.CustomerPortalService.*;
import br.com.luizcode.agenda.identity.AgendaUser;
import br.com.luizcode.agenda.identity.MailSettings;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomerPortalController {
    public static final String COOKIE="AGENDA_CUSTOMER_ACCESS";
    private final CustomerPortalService portal;
    private final MailSettings settings;
    private final Clock clock;
    public CustomerPortalController(CustomerPortalService portal,MailSettings settings,Clock clock){this.portal=portal;this.settings=settings;this.clock=clock;}
    @PostMapping("/api/customer/session")
    View open(@Valid @RequestBody Exchange body,HttpServletRequest request,HttpServletResponse response){
        var result=portal.open(body.token(),cookie(request));
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,result.secret()).httpOnly(true).secure(settings.secureCookie())
                .sameSite("Strict").path(path(result.view().reservationKey())).maxAge(Duration.between(clock.instant(),result.expiresAt())).build().toString());
        return result.view();
    }
    @GetMapping("/api/customer/reservations/{id}")
    View view(HttpServletRequest request,@PathVariable UUID id){return portal.read(cookie(request),id);}
    @GetMapping("/api/customer/reservations/{id}/availability")
    br.com.luizcode.agenda.scheduling.ScheduleModels.Availability availability(HttpServletRequest request,@PathVariable UUID id,@RequestParam LocalDate date){return portal.availability(cookie(request),id,date);}
    @PostMapping("/api/customer/reservations/{id}/cancel")
    View cancel(HttpServletRequest request,@PathVariable UUID id,@Valid @RequestBody Change body){return portal.cancel(cookie(request),id,body);}
    @PatchMapping("/api/customer/reservations/{id}/reschedule")
    View move(HttpServletRequest request,@PathVariable UUID id,@Valid @RequestBody Move body){return portal.move(cookie(request),id,body);}
    @DeleteMapping("/api/customer/reservations/{id}/session")
    ResponseEntity<Void> close(HttpServletRequest request,HttpServletResponse response,@PathVariable UUID id){
        portal.close(cookie(request),id);response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,"").httpOnly(true)
                .secure(settings.secureCookie()).sameSite("Strict").path(path(id)).maxAge(0).build().toString());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/admin/appointments/{id}/customer-access")
    AccessState state(@AuthenticationPrincipal AgendaUser owner,@PathVariable UUID id){return portal.state(owner.businessId(),id);}
    @PostMapping("/api/admin/appointments/{id}/customer-access")
    Issued issue(@AuthenticationPrincipal AgendaUser owner,@PathVariable UUID id,@Valid @RequestBody Issue body){return portal.issue(owner.businessId(),id,body);}
    @DeleteMapping("/api/admin/appointments/{id}/customer-access")
    AccessState revoke(@AuthenticationPrincipal AgendaUser owner,@PathVariable UUID id,@Valid @RequestBody Change body){return portal.revoke(owner.businessId(),id,body);}
    private static String path(UUID id){return "/api/customer/reservations/"+id;}
    private static String cookie(HttpServletRequest request){
        if(request.getCookies()!=null)for(var cookie:request.getCookies())if(cookie.getName().equals(COOKIE))return cookie.getValue();
        return null;
    }
}
