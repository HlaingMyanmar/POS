package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.service.PurchaseReturnDetailService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-return-details")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PurchaseReturnDetailController {

    private final PurchaseReturnDetailService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseReturnDetailDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Detail List Retrieved Successfully", service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseReturnDetailDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Detail Found", service.findById(id))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseReturnDetailDTO>> create(@Valid @RequestBody PurchaseReturnDetailDTO dto) {
        PurchaseReturnDetailDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Purchase Return Detail Created Successfully", created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseReturnDetailDTO>> update(
            @PathVariable Integer id,
            @Valid @RequestBody PurchaseReturnDetailDTO dto) {
        PurchaseReturnDetailDTO updated = service.update(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Detail Updated Successfully", updated)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DETAIL_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return Detail Deleted Successfully", null)
        );
    }
}
