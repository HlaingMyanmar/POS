package org.sspd.servicemgmt.servicebookingsettingsoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingArrivalWindowDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingDateExceptionDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingSettingsDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.dto.ServiceBookingWeekdayHoursDTO;
import org.sspd.servicemgmt.servicebookingsettingsoptions.service.ServiceBookingArrivalWindowService;
import org.sspd.servicemgmt.servicebookingsettingsoptions.service.ServiceBookingDateExceptionService;
import org.sspd.servicemgmt.servicebookingsettingsoptions.service.ServiceBookingSettingsService;
import org.sspd.servicemgmt.servicebookingsettingsoptions.service.ServiceBookingWeekdayHoursService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-booking-settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ServiceBookingSettingsController {

    private final ServiceBookingSettingsService service;
    private final ServiceBookingWeekdayHoursService weekdayHoursService;
    private final ServiceBookingArrivalWindowService arrivalWindowService;
    private final ServiceBookingDateExceptionService dateExceptionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_SERVICE_READ')")
    public ResponseEntity<ApiResponse<ServiceBookingSettingsDTO>> get() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service booking settings", service.getSettings()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<ServiceBookingSettingsDTO>> save(@RequestBody ServiceBookingSettingsDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", service.saveSettings(dto)));
    }

    // ── Weekday hours (multiple periods per day) ─────────────────

    @GetMapping("/weekday-hours")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_SERVICE_READ')")
    public ResponseEntity<ApiResponse<List<ServiceBookingWeekdayHoursDTO>>> listWeekdayHours() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Weekday hours", weekdayHoursService.list()));
    }

    @PostMapping("/weekday-hours")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<ServiceBookingWeekdayHoursDTO>> createWeekdayHours(
            @RequestBody ServiceBookingWeekdayHoursDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", weekdayHoursService.create(dto)));
    }

    @PutMapping("/weekday-hours/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<ServiceBookingWeekdayHoursDTO>> updateWeekdayHours(
            @PathVariable Integer id, @RequestBody ServiceBookingWeekdayHoursDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", weekdayHoursService.update(id, dto)));
    }

    @DeleteMapping("/weekday-hours/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> deleteWeekdayHours(@PathVariable Integer id) {
        weekdayHoursService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }

    // ── Arrival windows ──────────────────────────────────────────

    @GetMapping("/arrival-windows")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_SERVICE_READ')")
    public ResponseEntity<ApiResponse<List<ServiceBookingArrivalWindowDTO>>> listArrivalWindows() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Arrival windows", arrivalWindowService.list()));
    }

    @GetMapping("/arrival-windows/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_SERVICE_READ')")
    public ResponseEntity<ApiResponse<ServiceBookingArrivalWindowDTO>> getArrivalWindow(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Arrival window", arrivalWindowService.get(id)));
    }

    @PostMapping("/arrival-windows")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<ServiceBookingArrivalWindowDTO>> createArrivalWindow(
            @RequestBody ServiceBookingArrivalWindowDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", arrivalWindowService.create(dto)));
    }

    @PutMapping("/arrival-windows/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<ServiceBookingArrivalWindowDTO>> updateArrivalWindow(
            @PathVariable Integer id, @RequestBody ServiceBookingArrivalWindowDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", arrivalWindowService.update(id, dto)));
    }

    @DeleteMapping("/arrival-windows/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> deleteArrivalWindow(@PathVariable Integer id) {
        arrivalWindowService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }

    // ── Date exceptions ──────────────────────────────────────────

    @GetMapping("/date-exceptions")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_SERVICE_READ')")
    public ResponseEntity<ApiResponse<List<ServiceBookingDateExceptionDTO>>> listDateExceptions() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Date exceptions", dateExceptionService.list()));
    }

    @GetMapping("/date-exceptions/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_SERVICE_READ')")
    public ResponseEntity<ApiResponse<ServiceBookingDateExceptionDTO>> getDateException(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Date exception", dateExceptionService.get(id)));
    }

    @PostMapping("/date-exceptions")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<ServiceBookingDateExceptionDTO>> createDateException(
            @RequestBody ServiceBookingDateExceptionDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", dateExceptionService.create(dto)));
    }

    @PutMapping("/date-exceptions/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<ServiceBookingDateExceptionDTO>> updateDateException(
            @PathVariable Integer id, @RequestBody ServiceBookingDateExceptionDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", dateExceptionService.update(id, dto)));
    }

    @DeleteMapping("/date-exceptions/{id}")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_BOOKING_UPDATE','CAN_ACCESS_SERVICE_JOB_UPDATE','CAN_ACCESS_SERVICE_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> deleteDateException(@PathVariable Integer id) {
        dateExceptionService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
