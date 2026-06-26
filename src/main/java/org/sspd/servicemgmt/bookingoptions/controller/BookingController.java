package org.sspd.servicemgmt.bookingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PagedResponse;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.service.BookingService;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_READ')")
    @GetMapping
    ResponseEntity<ApiResponse<PagedResponse<BookingDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String dateFrom,
            @RequestParam(defaultValue = "") String dateTo) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Bookings",
                new PagedResponse<>(service.findAll(search, dateFrom, dateTo, page, size))));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<BookingDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Booking", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_READ')")
    @GetMapping("/status/{status}")
    ResponseEntity<ApiResponse<List<BookingDTO>>> getByStatus(@PathVariable BookingStatus status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Bookings", service.findByStatus(status)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_READ')")
    @GetMapping("/scan/{invoiceNo}")
    ResponseEntity<ApiResponse<BookingDTO>> getByInvoiceNo(@PathVariable String invoiceNo) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Booking", service.findByInvoiceNo(invoiceNo)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_READ')")
    @GetMapping("/upcoming")
    ResponseEntity<ApiResponse<List<BookingDTO>>> getUpcoming(
            @RequestParam(defaultValue = "60") int minutesAhead) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Upcoming", service.findUpcoming(minutesAhead)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_CREATE')")
    @PostMapping
    ResponseEntity<ApiResponse<BookingDTO>> create(@RequestBody BookingDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", service.save(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_UPDATE')")
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<BookingDTO>> update(@PathVariable Integer id, @RequestBody BookingDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_UPDATE')")
    @PatchMapping("/{id}/status")
    ResponseEntity<ApiResponse<BookingDTO>> updateStatus(
            @PathVariable Integer id, @RequestParam BookingStatus status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Status updated", service.updateStatus(id, status)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_CONVERT_JOB')")
    @PostMapping("/{id}/convert-to-job")
    ResponseEntity<ApiResponse<List<ServiceJobDTO>>> convertToJob(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Converted to Service Job(s)", service.convertToJob(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_DELETE')")
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
