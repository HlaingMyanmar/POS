package org.sspd.servicemgmt.servicejoboptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PagedResponse;
import org.sspd.servicemgmt.servicejoboptions.dto.ReworkRequestDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;
import org.sspd.servicemgmt.servicejoboptions.dto.SettleDTO;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.dto.HandoverDTO;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.service.ServiceJobTeamService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-jobs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ServiceJobController {

    private final ServiceJobService service;
    private final ServiceJobTeamService teamService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/pending-handovers/mine")
    ResponseEntity<ApiResponse<List<HandoverDTO>>> myPendingHandovers() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Pending handovers", teamService.myPendingHandovers()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/handovers/sent/mine")
    ResponseEntity<ApiResponse<List<HandoverDTO>>> mySentHandovers() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sent handovers", teamService.mySentHandovers()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping
    ResponseEntity<ApiResponse<PagedResponse<ServiceJobDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String dateFrom,
            @RequestParam(defaultValue = "") String dateTo,
            @RequestParam(required = false) Integer staffId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service Jobs",
                new PagedResponse<>(service.findAll(search, dateFrom, dateTo, page, size, staffId))));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/customer/{customerId}")
    ResponseEntity<ApiResponse<List<ServiceJobDTO>>> getByCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer service history", service.findByCustomerId(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/by-booking/{bookingId}")
    ResponseEntity<ApiResponse<List<ServiceJobDTO>>> getByBooking(@PathVariable Integer bookingId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Booking service jobs", service.findByBookingId(bookingId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<ServiceJobDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service Job", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/status/{status}")
    ResponseEntity<ApiResponse<List<ServiceJobDTO>>> getByStatus(@PathVariable ServiceJobStatus status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service Jobs", service.findByStatus(status)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/unpaid")
    ResponseEntity<ApiResponse<List<ServiceJobDTO>>> getUnpaid() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Unpaid Jobs", service.findUnpaid()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/overdue")
    ResponseEntity<ApiResponse<List<ServiceJobDTO>>> getOverdue() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Overdue Jobs", service.findOverdue()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/used-serial-numbers")
    ResponseEntity<ApiResponse<java.util.Set<String>>> getUsedSerialNumbers(
            @RequestParam(required = false) Integer excludeJobId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Used Serial Numbers",
                service.getUsedSerialNumbers(excludeJobId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_CREATE')")
    @PostMapping
    ResponseEntity<ApiResponse<ServiceJobDTO>> create(@RequestBody ServiceJobDTO dto) {
        return ResponseEntity.status(201).body(
            new ApiResponse<>(true, "Service Job Created", service.create(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<ServiceJobDTO>> update(
            @PathVariable Integer id, @RequestBody ServiceJobDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PatchMapping("/{id}/status")
    ResponseEntity<ApiResponse<ServiceJobDTO>> updateStatus(
            @PathVariable Integer id,
            @RequestParam ServiceJobStatus status,
            @RequestParam(required = false) String holdReason) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Status Updated", service.updateStatus(id, status, holdReason)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_SETTLE')")
    @PostMapping("/{id}/settle")
    ResponseEntity<ApiResponse<ServiceJobDTO>> settle(
            @PathVariable Integer id, @RequestBody SettleDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Settled", service.settle(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_SETTLE')")
    @PostMapping("/{id}/pay-due")
    ResponseEntity<ApiResponse<ServiceJobDTO>> payDue(
            @PathVariable Integer id, @RequestBody SettleDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Payment recorded", service.payDue(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PostMapping("/{id}/deliver")
    ResponseEntity<ApiResponse<ServiceJobDTO>> deliver(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Delivered", service.deliver(id)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_DUE_DELIVERY_APPROVE','ROLE_ADMINISTRATOR','ROLE_MANAGER')")
    @PostMapping("/{id}/approve-due-delivery")
    ResponseEntity<ApiResponse<ServiceJobDTO>> approveDueDelivery(
            @PathVariable Integer id, @RequestBody java.util.Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Due delivery approved", service.approveDueDelivery(id, reason)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_REWORK')")
    @PostMapping("/{id}/rework")
    ResponseEntity<ApiResponse<ServiceJobDTO>> rework(
            @PathVariable Integer id, @RequestBody ReworkRequestDTO dto) {
        return ResponseEntity.status(201).body(
            new ApiResponse<>(true, "Rework Job Created", service.createRework(id, dto)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_VOID','CAN_ACCESS_SERVICE_JOB_DELETE')")
    @PostMapping("/{id}/void")
    ResponseEntity<ApiResponse<ServiceJobDTO>> voidSettlement(
            @PathVariable Integer id, @RequestBody java.util.Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Settlement voided", service.voidSettlement(id, reason)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PostMapping("/{id}/approve-estimate")
    ResponseEntity<ApiResponse<ServiceJobDTO>> approveEstimate(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Estimate approved", service.approveEstimate(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PostMapping("/{id}/approve-final")
    public ResponseEntity<ApiResponse<ServiceJobDTO>> approveFinal(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Final approval completed", service.approveFinalCompletion(id)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SERVICE_JOB_WORK_LOG','CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/{id}/lead-final-check")
    public ResponseEntity<ApiResponse<ServiceJobDTO>> leadFinalCheck(@PathVariable Integer id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String note = body == null || body.get("note") == null ? null : String.valueOf(body.get("note"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Lead final check submitted",
                service.submitLeadFinalCheck(id, note)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_TECHNICIAN_ASSIGN')")
    @PostMapping("/{id}/return-final")
    public ResponseEntity<ApiResponse<ServiceJobDTO>> returnFinal(@PathVariable Integer id,
            @RequestBody java.util.Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Returned for rework",
                service.returnFinalCheck(id, reason)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PostMapping("/{id}/notify")
    ResponseEntity<ApiResponse<org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobNotificationDTO>> notifyCustomer(
            @PathVariable Integer id,
            @RequestBody org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobNotificationDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notification logged", service.notifyCustomer(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @PostMapping("/{id}/attachments")
    ResponseEntity<ApiResponse<org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobAttachmentDTO>> addAttachment(
            @PathVariable Integer id,
            @RequestBody org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobAttachmentDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Attached", service.addAttachment(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_UPDATE')")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    ResponseEntity<ApiResponse<Void>> deleteAttachment(@PathVariable Integer id, @PathVariable Integer attachmentId) {
        service.deleteAttachment(id, attachmentId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Attachment deleted", null));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_DELETE')")
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
