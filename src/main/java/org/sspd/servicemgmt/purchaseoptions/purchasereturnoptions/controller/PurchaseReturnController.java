package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnAttachmentDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service.PurchaseReturnService;

import java.util.List;
import java.time.LocalDateTime;

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
            @RequestParam(defaultValue = "") String search, @RequestParam(required=false) LocalDateTime from, @RequestParam(required=false) LocalDateTime to, @RequestParam(required=false) Integer supplierId, @RequestParam(required=false) Integer purchaseId, @RequestParam(required=false) String status, @RequestParam(required=false) String settlementType, @RequestParam(required=false) String resolutionType) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Return List Retrieved Successfully", service.findAll(search,from,to,supplierId,purchaseId,status,settlementType,resolutionType,page,size))
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

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> submit(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Return Submitted", service.submit(id)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> approve(
            @PathVariable Integer id, @RequestBody(required = false) PurchaseReturnDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Return Approved", service.approve(id, dto)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> reject(@PathVariable Integer id, @RequestBody PurchaseReturnDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Return Sent Back", service.reject(id, dto)));
    }
    @PostMapping("/{id}/attachments") public ResponseEntity<ApiResponse<PurchaseReturnDTO>> addAttachment(@PathVariable Integer id,@RequestBody PurchaseReturnAttachmentDTO dto){ return ResponseEntity.ok(new ApiResponse<>(true,"Attachment added",service.addAttachment(id,dto))); }
    @DeleteMapping("/{id}/attachments/{attachmentId}") public ResponseEntity<ApiResponse<PurchaseReturnDTO>> deleteAttachment(@PathVariable Integer id,@PathVariable Integer attachmentId){ return ResponseEntity.ok(new ApiResponse<>(true,"Attachment deleted",service.deleteAttachment(id,attachmentId))); }

    @PostMapping("/{id}/dispatch")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> dispatch(
            @PathVariable Integer id, @RequestBody PurchaseReturnDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Return Dispatched", service.dispatch(id, dto)));
    }

    @PostMapping("/{id}/supplier-received")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> supplierReceived(
            @PathVariable Integer id, @RequestBody(required = false) PurchaseReturnDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Supplier Receipt Recorded", service.supplierReceived(id, dto)));
    }

    @PostMapping("/{id}/settle")
    public ResponseEntity<ApiResponse<PurchaseReturnDTO>> settle(
            @PathVariable Integer id, @RequestBody PurchaseReturnDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Return Settled", service.settle(id, dto)));
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
