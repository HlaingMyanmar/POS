package org.sspd.servicemgmt.quotationoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.quotationoptions.dto.QuotationDTO;
import org.sspd.servicemgmt.quotationoptions.model.QuotationStatus;
import org.sspd.servicemgmt.quotationoptions.service.QuotationService;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
public class QuotationController {
    private final QuotationService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_QUOTATION_READ')")
    @GetMapping public ResponseEntity<ApiResponse<List<QuotationDTO>>> all() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Quotations retrieved", service.findAll()));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_QUOTATION_READ')")
    @GetMapping("/{id}") public ResponseEntity<ApiResponse<QuotationDTO>> one(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Quotation retrieved", service.findById(id)));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_QUOTATION_CREATE')")
    @PostMapping public ResponseEntity<ApiResponse<QuotationDTO>> create(@RequestBody QuotationDTO dto) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Quotation created", service.create(dto)));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_QUOTATION_UPDATE')")
    @PutMapping("/{id}") public ResponseEntity<ApiResponse<QuotationDTO>> update(@PathVariable Integer id, @RequestBody QuotationDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Quotation updated", service.update(id, dto)));
    }
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_QUOTATION_UPDATE','CAN_ACCESS_QUOTATION_CANCEL')")
    @PatchMapping("/{id}/status") public ResponseEntity<ApiResponse<QuotationDTO>> status(@PathVariable Integer id, @RequestParam QuotationStatus status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Quotation status updated", service.changeStatus(id, status)));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_QUOTATION_CONVERT_TO_SALE') and hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @PostMapping("/{id}/convert-to-sale") public ResponseEntity<ApiResponse<SaleDTO>> convert(@PathVariable Integer id, @RequestBody(required = false) SaleDTO sale) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Quotation converted to sale", service.convertToSale(id, sale)));
    }
}
