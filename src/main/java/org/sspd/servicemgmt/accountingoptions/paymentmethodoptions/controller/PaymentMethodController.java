package org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.dto.PaymentMethodDTO;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentMethodService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payment-methods")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService service;


    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentMethodDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Payment Method List", service.findAll())
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_READ')")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<PaymentMethodDTO>>> getAllActive() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Active Payment Method List", service.findAllActive())
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentMethodDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Payment Method Found", service.findById(id))
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentMethodDTO>> create(
            @Valid @RequestBody PaymentMethodDTO dto) {
        PaymentMethodDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Payment Method Created Successfully", created)
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentMethodDTO>> update(
            @PathVariable Integer id,
            @Valid @RequestBody PaymentMethodDTO dto) {
        PaymentMethodDTO updated = service.update(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Payment Method Updated Successfully", updated)
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_METHOD_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Payment Method Deleted Successfully", null)
        );
    }
}