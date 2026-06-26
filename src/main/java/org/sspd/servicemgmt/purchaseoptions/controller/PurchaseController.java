package org.sspd.servicemgmt.purchaseoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;
import org.sspd.servicemgmt.purchaseoptions.service.PurchaseService;

@RestController
@RequestMapping("/api/v1/purchases")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService service;

    /**
     * ၁။ အဝယ်ဘောက်ချာ အားလုံးကိုကြည့်ခြင်း
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PurchaseDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase List Retrieved Successfully", service.findAll(search, page, size))
        );
    }

    /**
     * ၂။ ID ဖြင့် အဝယ်ဘောက်ချာ အသေးစိတ်ကိုရှာခြင်း
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseDTO>> getById(@PathVariable Integer id) {
        PurchaseDTO purchase = service.findById(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Details Found", purchase)
        );
    }

    /**
     * ၃။ အဝယ်ဘောက်ချာ အသစ်သိမ်းဆည်းခြင်း
     * (ဤ API ကို ခေါ်လိုက်ပါက Stock, Serial, Accounting အားလုံး Auto အလုပ်လုပ်ပါမည်)
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseDTO>> create(@Valid @RequestBody PurchaseDTO dto) {
        PurchaseDTO createdPurchase = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Purchase Created and Journal Posted Successfully", createdPurchase)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseDTO>> update(@PathVariable Integer id,
                                                           @Valid @RequestBody PurchaseDTO dto) {
        PurchaseDTO updatedPurchase = service.update(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Updated Successfully", updatedPurchase)
        );
    }
}
