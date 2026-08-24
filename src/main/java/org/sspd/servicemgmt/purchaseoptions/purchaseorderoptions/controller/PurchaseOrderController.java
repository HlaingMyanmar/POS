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

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Integer id) {
        service.cancel(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Order Cancelled", null));
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
}
