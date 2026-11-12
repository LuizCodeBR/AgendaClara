package br.com.luizcode.agenda.scheduling;

import static br.com.luizcode.agenda.catalog.CatalogModels.*;
import static br.com.luizcode.agenda.scheduling.ScheduleModels.*;
import br.com.luizcode.agenda.catalog.CatalogRepository;
import br.com.luizcode.agenda.identity.AgendaUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ScheduleController {
    private final CatalogRepository catalog;
    private final ScheduleService schedule;
    private final boolean demo;
    private final boolean publicDemo;
    private final String sourceUrl;
    private final String downloadUrl;
    public ScheduleController(CatalogRepository catalog, ScheduleService schedule,
                              @Value("${app.demo.enabled:false}") boolean demo,
                              @Value("${app.demo.public:false}") boolean publicDemo,
                              @Value("${app.distribution.source-url:}") String sourceUrl,
                              @Value("${app.distribution.download-url:}") String downloadUrl) {
        this.catalog = catalog; this.schedule = schedule; this.demo = demo;
        this.publicDemo = publicDemo; this.sourceUrl = sourceUrl; this.downloadUrl = downloadUrl;
    }
    @GetMapping("/api/public/config")
    Map<String, Object> config() { return Map.of("demo", demo, "publicDemo", publicDemo, "version", "0.2.1",
            "distribution", Map.of("sourceUrl", sourceUrl, "downloadUrl", downloadUrl)); }
    @GetMapping("/api/public/businesses")
    List<Business> businesses() { return catalog.businesses(); }
    @GetMapping("/api/public/{slug}/catalog")
    Catalog catalog(@PathVariable String slug) { return schedule.catalog(catalog.business(slug)); }
    @GetMapping("/api/public/{slug}/availability")
    Availability availability(@PathVariable String slug, @RequestParam UUID professionalId,
                              @RequestParam UUID serviceId, @RequestParam LocalDate date) {
        return schedule.availability(catalog.business(slug), professionalId, serviceId, date);
    }
    @PostMapping("/api/public/{slug}/bookings")
    ResponseEntity<BookingReceipt> book(@PathVariable String slug, @Valid @RequestBody BookingRequest request) {
        BookingOutcome outcome = schedule.book(catalog.business(slug), request);
        return ResponseEntity.status(outcome.created() ? 201 : 200).body(outcome.receipt());
    }
    @GetMapping("/api/admin/catalog")
    Catalog adminCatalog(@AuthenticationPrincipal AgendaUser user) {
        return schedule.catalog(catalog.business(user.businessId()));
    }
    @GetMapping("/api/admin/agenda")
    DayAgenda agenda(@AuthenticationPrincipal AgendaUser user, @RequestParam LocalDate date) {
        return schedule.day(catalog.business(user.businessId()), date);
    }
    @PostMapping("/api/admin/appointments/{id}/cancel")
    AppointmentView cancel(@AuthenticationPrincipal AgendaUser user, @PathVariable UUID id) {
        return schedule.cancel(catalog.business(user.businessId()), id);
    }
    @PatchMapping("/api/admin/appointments/{id}/reschedule")
    AppointmentView reschedule(@AuthenticationPrincipal AgendaUser user, @PathVariable UUID id,
                               @Valid @RequestBody RescheduleRequest request) {
        return schedule.reschedule(catalog.business(user.businessId()), id, request);
    }
    @PostMapping("/api/admin/blocks")
    ResponseEntity<BlockView> block(@AuthenticationPrincipal AgendaUser user, @Valid @RequestBody BlockRequest request) {
        return ResponseEntity.status(201).body(schedule.block(catalog.business(user.businessId()), request));
    }
    @DeleteMapping("/api/admin/blocks/{id}")
    ResponseEntity<Void> removeBlock(@AuthenticationPrincipal AgendaUser user, @PathVariable UUID id) {
        schedule.removeBlock(catalog.business(user.businessId()), id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/api/admin/services")
    ResponseEntity<br.com.luizcode.agenda.catalog.CatalogModels.Service> createService(
            @AuthenticationPrincipal AgendaUser user, @Valid @RequestBody CreateService request) {
        return ResponseEntity.status(201).body(catalog.createService(user.businessId(), request));
    }
}
