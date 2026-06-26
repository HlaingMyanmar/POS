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
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.service.ServiceJobService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-jobs")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ServiceJobController {

    private final ServiceJobService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping
    ResponseEntity<ApiResponse<PagedResponse<ServiceJobDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String dateFrom,
            @RequestParam(defaultValue = "") String dateTo) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service Jobs",
                new PagedResponse<>(service.findAll(search, dateFrom, dateTo, page, size))));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<ServiceJobDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service Job", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/by-booking/{bookingId}")
    ResponseEntity<ApiResponse<ServiceJobDTO>> getByBooking(@PathVariable Integer bookingId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service Job", service.findByBookingId(bookingId)));
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
            @PathVariable Integer id, @RequestParam ServiceJobStatus status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Status Updated", service.updateStatus(id, status)));
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

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_REWORK')")
    @PostMapping("/{id}/rework")
    ResponseEntity<ApiResponse<ServiceJobDTO>> rework(
            @PathVariable Integer id, @RequestBody ReworkRequestDTO dto) {
        return ResponseEntity.status(201).body(
            new ApiResponse<>(true, "Rework Job Created", service.createRework(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_DELETE')")
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
