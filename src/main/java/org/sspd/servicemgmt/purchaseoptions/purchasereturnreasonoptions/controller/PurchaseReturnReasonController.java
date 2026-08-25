package org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.dto.PurchaseReturnReasonDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.service.PurchaseReturnReasonService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/purchase-return-reasons")
@RequiredArgsConstructor
public class PurchaseReturnReasonController {
    private final PurchaseReturnReasonService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseReturnReasonDTO>>> all(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase return reasons", service.findAll(activeOnly)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseReturnReasonDTO>> create(@RequestBody PurchaseReturnReasonDTO dto) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Reason created", service.save(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseReturnReasonDTO>> update(
            @PathVariable Integer id, @RequestBody PurchaseReturnReasonDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Reason updated", service.update(id, dto)));
    }
}
