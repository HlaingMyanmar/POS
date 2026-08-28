package org.sspd.servicemgmt.saleoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.dto.SalePaymentDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;

@RestController
@RequestMapping("/api/v1/sales")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService service;
    private final org.sspd.servicemgmt.saleoptions.service.SaleInsightService insightService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SaleDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String dateFrom,
            @RequestParam(defaultValue = "") String dateTo
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale list retrieved", service.findAll(search, dateFrom, dateTo, page, size)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> stats(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale stats", insightService.stats(dateFrom, dateTo)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<java.util.List<SaleDTO>>> getByCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer sale history", service.findByCustomerId(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleDTO>> getById(@PathVariable Integer id) {
        SaleDTO sale = service.findById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale found", sale));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<java.util.List<org.sspd.servicemgmt.saleoptions.dto.SaleTimelineEventDTO>>> timeline(
            @PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale timeline", insightService.timeline(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String dateFrom,
                                              @RequestParam(required = false) String dateTo) throws java.io.IOException {
        byte[] bytes = service.exportExcel(dateFrom, dateTo);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=sales_" + java.time.LocalDate.now() + ".xlsx")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<SaleDTO>> create(@Valid @RequestBody SaleDTO dto) {
        SaleDTO created = service.save(dto);
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Sale created successfully", created));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleDTO>> update(@PathVariable Integer id,
                                                       @Valid @RequestBody SaleDTO dto) {
        SaleDTO updated = service.update(id, dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale updated successfully", updated));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<SaleDTO>> pay(@PathVariable Integer id,
                                                    @Valid @RequestBody SalePaymentDTO dto) {
        SaleDTO paid = service.payDue(id, dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale payment recorded", paid));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_VOID','CAN_ACCESS_SALE_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleDTO>> delete(@PathVariable Integer id,
                                                       @RequestParam String reason) {
        SaleDTO voided = service.voidSale(id, reason);
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale voided successfully", voided));
    }
}
