package org.sspd.servicemgmt.creditoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.saleoptions.dto.SalePaymentDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer-payments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final CustomerPaymentService paymentService;
    private final SaleService saleService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CustomerPaymentDTO dto) {
        if (dto.getSaleId() != null) {
            SalePaymentDTO payDto = new SalePaymentDTO();
            payDto.setPaidAmount(dto.getAmount());
            payDto.setPaymentMethodId(dto.getPaymentMethodId());
            payDto.setPaymentAccountId(null);
            payDto.setTransactionNo(dto.getTransactionNo());
            payDto.setArAccountId(null);
            payDto.setStaffId(dto.getStaffId());
            payDto.setNote(dto.getNote());
            var sale = saleService.payDue(dto.getSaleId(), payDto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Sale payment recorded", sale));
        }
        return ResponseEntity.status(201)
                .body(new ApiResponse<>(true, "Advance payment created", paymentService.createAdvancePayment(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<CustomerPaymentDTO>>> byCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer payments retrieved", paymentService.findByCustomer(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/sale/{saleId}")
    public ResponseEntity<ApiResponse<List<CustomerPaymentDTO>>> bySale(@PathVariable Integer saleId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale payments retrieved", paymentService.findBySale(saleId)));
    }
}
