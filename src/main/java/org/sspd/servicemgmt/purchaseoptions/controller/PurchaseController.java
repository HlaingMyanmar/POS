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
    private final org.sspd.servicemgmt.purchaseoptions.service.PurchaseImportService importService;
    private final org.sspd.servicemgmt.purchaseoptions.service.PurchaseInsightService insightService;

    /**
     * ၁။ အဝယ်ဘောက်ချာ အားလုံးကိုကြည့်ခြင်း
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PurchaseDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase List Retrieved Successfully", service.findAll(search, dateFrom, dateTo, page, size))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getStats(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Stats", service.getStats(dateFrom, dateTo))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/trend")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getTrend(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Trend", service.getTrend(dateFrom, dateTo)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<org.sspd.servicemgmt.purchaseoptions.dto.PurchaseAnalyticsDTO>> analytics(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase analytics", insightService.analytics(dateFrom, dateTo)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @PostMapping("/budget-check")
    public ResponseEntity<ApiResponse<org.sspd.servicemgmt.purchaseoptions.budget.dto.PurchaseBudgetCheckDTO>> budgetCheck(@RequestBody PurchaseDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Budget check", service.checkBudget(dto)));
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

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<java.util.List<org.sspd.servicemgmt.purchaseoptions.dto.PurchaseTimelineEventDTO>>> timeline(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase timeline", insightService.timeline(id)));
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

    /**
     * ၃။(က) Supplier invoice attachment ပြောင်းလဲခြင်း / ဖယ်ရှားခြင်း (metadata only — stock/accounting မထိ)
     * body: { "attachmentName": "...", "attachmentData": "data:image/png;base64,..." } — null both = remove
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_UPDATE')")
    @PutMapping("/{id}/attachment")
    public ResponseEntity<ApiResponse<PurchaseDTO>> updateAttachment(@PathVariable Integer id,
                                                                     @RequestBody java.util.Map<String, String> body) {
        PurchaseDTO updatedPurchase = service.updateAttachment(id, body.get("attachmentName"), body.get("attachmentData"));
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Attachment Updated Successfully", updatedPurchase)
        );
    }

    /**
     * ၄။ Draft voucher → Confirm (stock, serial, accounting side effects run here)
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_UPDATE')")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<PurchaseDTO>> confirm(@PathVariable Integer id,
                                                            @RequestBody(required = false) PurchaseDTO dto) {
        PurchaseDTO confirmed = service.confirmDraft(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Confirmed Successfully", confirmed)
        );
    }

    /**
     * ၅။ Cancel / Void — draft is deleted; confirmed is reversed (stock + journal)
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseDTO>> cancel(@PathVariable Integer id,
                                                            @RequestParam String reason,
                                                            @RequestParam(required = false) Integer refundPaymentMethodId) {
        PurchaseDTO cancelled = service.cancel(id, reason, refundPaymentMethodId);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Purchase Cancelled Successfully", cancelled)
        );
    }

    /**
     * ၆။ Overdue payables — vouchers past their due date with remaining balance
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<java.util.List<PurchaseDTO>>> getOverdue() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Overdue Payables Retrieved", service.getOverdue())
        );
    }

    /**
     * ၇။ Reorder suggestions — products at/below reorder level for quick purchase drafting
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/reorder-suggestions")
    public ResponseEntity<ApiResponse<java.util.List<org.sspd.servicemgmt.purchaseoptions.dto.ReorderSuggestionDTO>>> getReorderSuggestions() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Reorder Suggestions Retrieved", service.getReorderSuggestions())
        );
    }

    /**
     * ၈။ Top suppliers by purchase amount
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/top-suppliers")
    public ResponseEntity<ApiResponse<java.util.List<java.util.Map<String, Object>>>> getTopSuppliers(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Top Suppliers Retrieved", service.getTopSuppliers(dateFrom, dateTo))
        );
    }

    /**
     * ၈။ Excel export (.xlsx) of purchases in a date range
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String dateFrom,
                                              @RequestParam(required = false) String dateTo) throws java.io.IOException {
        byte[] bytes = service.exportExcel(dateFrom, dateTo);
        String filename = "purchases_" + java.time.LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_CREATE')")
    @PostMapping(value="/import/preview",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<org.sspd.servicemgmt.purchaseoptions.dto.PurchaseImportPreviewDTO>> previewImport(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        return ResponseEntity.ok(new ApiResponse<>(true,"Purchase import preview",importService.preview(file)));
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_CREATE')")
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() throws java.io.IOException {
        return ResponseEntity.ok().header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=purchase_import_template.xlsx")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importService.template());
    }
}
