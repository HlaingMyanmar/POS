package org.sspd.servicemgmt.bookingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PagedResponse;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.bookingoptions.dto.BookingItemDTO;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.bookingoptions.service.BookingService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<BookingDTO>>> list(
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
    public ResponseEntity<ApiResponse<BookingDTO>> detail(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Booking", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<BookingDTO>> create(@RequestBody BookingDTO dto) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Booking created", service.create(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingDTO>> update(@PathVariable Integer id, @RequestBody BookingDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Booking updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_UPDATE')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BookingDTO>> updateStatus(
            @PathVariable Integer id, @RequestParam BookingStatus status) {
        if (status != BookingStatus.CANCELED)
            throw new IllegalArgumentException("Only CANCELED status can be set manually");
        return ResponseEntity.ok(new ApiResponse<>(true, "Booking canceled", service.cancel(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_UPDATE')")
    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<BookingDTO>> addItems(
            @PathVariable Integer id, @RequestBody List<BookingItemDTO> items) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Items received", service.addItems(id, items)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_UPDATE')")
    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<BookingDTO>> removeItem(
            @PathVariable Integer id, @PathVariable Integer itemId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Item removed", service.removeItem(id, itemId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_CONVERT_JOB')")
    @PostMapping("/{id}/convert-outdoor")
    public ResponseEntity<ApiResponse<BookingDTO>> convertOutdoor(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Outdoor service job created", service.convertOutdoor(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_CONVERT_JOB')")
    @PostMapping("/{id}/convert-indoor")
    public ResponseEntity<ApiResponse<BookingDTO>> convertIndoor(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Indoor service jobs created", service.convertIndoor(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BOOKING_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Booking deleted", null));
    }
}
