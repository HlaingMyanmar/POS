package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service.PurchaseReturnService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-returns")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PurchaseReturnDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return List Retrieved Successfully", service.findAll(search, page, size))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_READ')")
    @GetMapping("/by-purchase/{purchaseId}")
    public ResponseEntity<ApiResponse<List<PurchaseReturnDTO>>> getByPurchaseId(@PathVariable Integer purchaseId) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Returns", service.findByPurchaseId(purchaseId))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Details Found", service.findById(id))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> create(@Valid @RequestBody PurchaseReturnDTO dto) {
        PurchaseReturnDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Purchase Return Created Successfully", created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> update(
            @PathVariable Integer id,
            @Valid @RequestBody PurchaseReturnDTO dto) {
        PurchaseReturnDTO updated = service.update(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Updated Successfully", updated)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_UPDATE')")
    @PostMapping("/{id}/void")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> voidReturn(
            @PathVariable Integer id,
            @RequestBody PurchaseReturnDTO dto) {
        PurchaseReturnDTO updated = service.voidReturn(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Voided Successfully", updated)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Deleted Successfully", null)
        );
    }
}
