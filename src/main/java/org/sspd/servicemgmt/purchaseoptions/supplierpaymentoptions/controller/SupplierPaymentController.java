package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto.*;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.service.SupplierPaymentService;
import java.util.List;

@RestController @RequestMapping("/api/v1/supplier-payments") @RequiredArgsConstructor
public class SupplierPaymentController {
    private final SupplierPaymentService service;
    @PostMapping
    public ResponseEntity<ApiResponse<SupplierPaymentDTO>> pay(@RequestBody SupplierPaymentRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Supplier payment allocated", service.pay(request)));
    }
    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<ApiResponse<List<SupplierPaymentDTO>>> history(@PathVariable Integer supplierId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Supplier payment history", service.history(supplierId)));
    }
    @GetMapping("/supplier/{supplierId}/payables")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> payables(@PathVariable Integer supplierId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Supplier payables", service.payables(supplierId)));
    }
    @GetMapping("/supplier/{supplierId}/credit-summary")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> creditSummary(@PathVariable Integer supplierId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Supplier credit summary", service.creditSummary(supplierId)));
    }
    @PostMapping("/apply-credit")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> applyCredit(@RequestBody SupplierCreditApplyRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Supplier credit applied", service.applyCredit(request)));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<ApiResponse<SupplierPaymentDTO>> voidPayment(@PathVariable Integer id, @RequestBody java.util.Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        Integer staffId = body.get("staffId") == null ? null : Integer.valueOf(String.valueOf(body.get("staffId")));
        return ResponseEntity.ok(new ApiResponse<>(true, "Supplier payment voided", service.voidPayment(id, reason, staffId)));
    }
}
