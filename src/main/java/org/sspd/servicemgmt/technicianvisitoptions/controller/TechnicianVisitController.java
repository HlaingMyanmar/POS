package org.sspd.servicemgmt.technicianvisitoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.technicianvisitoptions.dto.LocationPingRequest;
import org.sspd.servicemgmt.technicianvisitoptions.dto.LocationPingDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.DeleteGpsHistoryRequest;
import org.sspd.servicemgmt.technicianvisitoptions.dto.TechnicianVisitDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.TechnicianVisitReportDTO;
import org.sspd.servicemgmt.technicianvisitoptions.dto.VisitReasonRequest;
import org.sspd.servicemgmt.technicianvisitoptions.service.TechnicianVisitService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/technician-visits")
@RequiredArgsConstructor
public class TechnicianVisitController {

    private final TechnicianVisitService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> start(
            @RequestParam Integer jobId,
            @RequestParam(defaultValue = "SERVICE") String purpose,
            @RequestBody LocationPingRequest ping,
            Authentication authentication
    ) {
        return ok("Visit started", service.start(jobId, purpose, ping, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/arrive")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> arrive(
            @PathVariable Long id,
            @RequestBody LocationPingRequest ping,
            Authentication authentication
    ) {
        return ok("Arrived", service.arrive(id, ping, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/depart-customer")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> departCustomer(
            @PathVariable Long id,
            @RequestBody LocationPingRequest ping,
            @RequestParam(defaultValue = "FIXED_ON_SITE") String outcome,
            @RequestParam(required = false) String note,
            Authentication authentication
    ) {
        return ok("Departed customer", service.departCustomer(id, outcome, note, ping, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> end(
            @PathVariable Long id,
            @RequestBody LocationPingRequest ping,
            Authentication authentication
    ) {
        return ok("Visit ended", service.end(id, ping, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> cancel(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication
    ) {
        return ok("Visit cancelled", service.cancel(id, body == null ? null : body.get("reason"), authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/ping")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> ping(
            @PathVariable Long id,
            @RequestBody LocationPingRequest ping,
            Authentication authentication
    ) {
        return ok("Location received", service.ping(id, ping, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/pings")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> pingBatch(
            @PathVariable Long id,
            @RequestBody List<LocationPingRequest> pings,
            Authentication authentication
    ) {
        return ok("Locations received", service.pingBatch(id, pings, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/reason")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> reason(
            @PathVariable Long id,
            @RequestBody VisitReasonRequest request,
            Authentication authentication
    ) {
        return ok("Reason saved", service.addReason(id, request, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @PostMapping("/{id}/resume-journey")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> resumeJourney(
            @PathVariable Long id,
            @RequestBody LocationPingRequest ping,
            Authentication authentication
    ) {
        return ok("Journey resumed", service.resumeJourney(id, ping, authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @GetMapping("/me/active")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> active(Authentication authentication) {
        return ok("Active visit", service.active(authentication));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_READ')")
    @GetMapping("/live")
    public ResponseEntity<ApiResponse<List<TechnicianVisitDTO>>> live() {
        return ok("Live locations", service.live());
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_READ')")
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<TechnicianVisitDTO>>> today() {
        return ok("Today's visits", service.today());
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_READ')")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<TechnicianVisitDTO>>> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ok("Visit history", service.history(from, to));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_READ')")
    @GetMapping("/report")
    public ResponseEntity<ApiResponse<List<TechnicianVisitReportDTO>>> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String job,
            @RequestParam(required = false) String customer
    ) {
        return ok("Visit report", service.report(from, to, job, customer));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TechnicianVisitDTO>> detail(@PathVariable Long id) {
        return ok("Visit detail", service.historyDetail(id));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_VISIT_START')")
    @GetMapping("/{id}/my-pings")
    public ResponseEntity<ApiResponse<List<LocationPingDTO>>> myPings(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ok("My visit GPS history", service.myPings(id, authentication));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_READ')")
    @GetMapping("/{id}/history-pings")
    public ResponseEntity<ApiResponse<List<LocationPingDTO>>> historyPings(@PathVariable Long id) {
        return ok("Visit GPS history", service.historyPings(id));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_TECHNICIAN_LOCATION_HISTORY_DELETE')")
    @DeleteMapping("/{id}/history-pings")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> deleteHistoryPings(
            @PathVariable Long id,
            @RequestBody DeleteGpsHistoryRequest request,
            Authentication authentication
    ) {
        int deleted = service.deleteVisitGpsHistory(
                id,
                request == null ? null : request.confirmation(),
                request == null ? null : request.reason(),
                authentication
        );
        return ok("Visit raw GPS history deleted", Map.of("deletedPoints", deleted));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(new ApiResponse<>(true, message, data));
    }
}
