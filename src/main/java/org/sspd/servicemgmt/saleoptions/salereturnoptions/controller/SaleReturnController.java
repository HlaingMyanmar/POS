package org.sspd.servicemgmt.saleoptions.salereturnoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.dto.SaleReturnDTO;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.service.SaleReturnService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sale-returns")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SaleReturnController {

    private final SaleReturnService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SaleReturnDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale Return List Retrieved Successfully", service.findAll(search, page, size)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_READ')")
    @GetMapping("/by-sale/{saleId}")
    public ResponseEntity<ApiResponse<List<SaleReturnDTO>>> getBySaleId(@PathVariable Integer saleId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale Returns by Sale", service.findBySaleId(saleId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleReturnDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale Return Details Found", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<SaleReturnDTO>> create(@Valid @RequestBody SaleReturnDTO dto) {
        SaleReturnDTO created = service.save(dto);
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Sale Return Created Successfully", created));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleReturnDTO>> update(@PathVariable Integer id, @Valid @RequestBody SaleReturnDTO dto) {
        SaleReturnDTO updated = service.update(id, dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale Return Updated Successfully", updated));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_RETURN_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id,
                                                    @RequestParam(required = false) String reason) {
        service.voidReturn(id, reason == null || reason.isBlank() ? "Voided" : reason);
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale Return Voided Successfully", null));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_RETURN_DELETE','CAN_ACCESS_SALE_RETURN_UPDATE')")
    @PostMapping("/{id}/void")
    public ResponseEntity<ApiResponse<SaleReturnDTO>> voidReturn(
            @PathVariable Integer id, @RequestBody(required = false) SaleReturnDTO dto) {
        String reason = dto == null || dto.getVoidReason() == null ? "Voided" : dto.getVoidReason();
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale Return Voided Successfully", service.voidReturn(id, reason)));
    }
}