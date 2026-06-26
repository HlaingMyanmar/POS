package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.AccountTransferDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service.PaymentTransactionService;
import org.sspd.servicemgmt.purchaseoptions.service.PurchaseService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-transactions")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PaymentTransactionController {

    private final PaymentTransactionService service;
    private final PurchaseService purchaseService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentTransactionDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Transaction List", service.findAll())
        );
    }
    // ၂။ အသေးစိတ် Report ထုတ်ခြင်း (Supplier Name, Reference Code ပါဝင်သည်)
    // *** ဒါကို UI developer ဆီ ပေးလိုက်ပါ ***
    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @GetMapping("/report")
    public ResponseEntity<ApiResponse<List<PaymentTransactionDTO>>> getDetailedReport() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Transaction Detailed Report", service.findAllWithDetails())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @GetMapping("/reference/{refId}")
    public ResponseEntity<ApiResponse<List<PaymentTransactionDTO>>> getByRef(
            @PathVariable Integer refId,
            @RequestParam String type) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Transactions for " + type, service.findByReference(refId, type))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentTransactionDTO>> create(
            @Valid @RequestBody PaymentTransactionDTO dto) {
        PaymentTransactionDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Transaction Recorded Successfully", created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<PaymentTransactionDTO>> transfer(
            @Valid @RequestBody AccountTransferDTO dto) {
        PaymentTransactionDTO created = service.transfer(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Transfer Recorded Successfully", created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @PostMapping("/pay-purchase-debt")
    public ResponseEntity<ApiResponse<PaymentTransactionDTO>> payDebt(
            @Valid @RequestBody PaymentTransactionDTO dto) {

        PaymentTransactionDTO result = purchaseService.payPurchaseDebt(dto);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Debt Payment Successful", result)
        );
    }
}
