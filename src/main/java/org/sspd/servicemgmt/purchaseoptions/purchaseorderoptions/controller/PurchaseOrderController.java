package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderReceiveDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderReceiveResultDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.service.PurchaseOrderService;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    /**
     * ၁။ Purchase Order အားလုံး ကြည့်ရှုခြင်း
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PurchaseOrderDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Orders Retrieved", service.findAll(search, page, size))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<java.util.List<PurchaseOrderDTO>>> getLate() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Late purchase orders", service.findLate()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @GetMapping("/{id}/goods-receipts")
    public ResponseEntity<ApiResponse<java.util.List<org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.GoodsReceiptDTO>>> getGoodsReceipts(
            @PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Goods Receipts", service.findGoodsReceipts(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Order Found", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> create(@RequestBody PurchaseOrderDTO dto) {
        return ResponseEntity.status(201)
                .body(new ApiResponse<>(true, "Purchase Order Created", service.save(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> update(@PathVariable Integer id,
                                                                @RequestBody PurchaseOrderDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Order Updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_DELETE') or hasAuthority('CAN_ACCESS_PURCHASE_ORDER_CANCEL_APPROVED')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Integer id) {
        service.cancel(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Order Cancelled", null));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_APPROVE') or hasAuthority('CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> approve(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Order Approved", service.approve(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_APPROVE') or hasAuthority('CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> reject(@PathVariable Integer id,
                                                                @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Order Rejected",
                service.reject(id, body.get("reason"))));
    }

    /**
     * ✅ Goods Receipt — PO lines → Purchase voucher
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_RECEIVE')")
    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<PurchaseOrderReceiveResultDTO>> receive(@PathVariable Integer id,
                                                                              @RequestBody PurchaseOrderReceiveDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Goods Received Successfully", service.receive(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_APPROVE')")
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<PurchaseOrderDTO>> close(@PathVariable Integer id,
                                                               @RequestBody(required = false) java.util.Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Order closed", service.close(id, reason)));
    }
}
