package org.sspd.servicemgmt.creditoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.creditoptions.dto.CustomerCreditApplyRequest;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentDTO;
import org.sspd.servicemgmt.creditoptions.dto.CustomerPaymentRequest;
import org.sspd.servicemgmt.creditoptions.service.CustomerPaymentService;
import org.sspd.servicemgmt.saleoptions.dto.SalePaymentDTO;
import org.sspd.servicemgmt.saleoptions.service.SaleService;

import java.util.List;
import java.util.Map;

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

    @PostMapping("/allocate")
    public ResponseEntity<ApiResponse<CustomerPaymentDTO>> allocate(@RequestBody CustomerPaymentRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Customer payment allocated", paymentService.allocate(request)));
    }

    @PostMapping("/apply-credit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyCredit(@RequestBody CustomerCreditApplyRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Customer credit applied", paymentService.applyCredit(request)));
    }

    @PostMapping("/{id}/void")
    public ResponseEntity<ApiResponse<CustomerPaymentDTO>> voidPayment(
            @PathVariable Integer id, @RequestBody Map<String, Object> body) {
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        Integer staffId = body.get("staffId") == null ? null : Integer.valueOf(String.valueOf(body.get("staffId")));
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer payment voided", paymentService.voidPayment(id, reason, staffId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<CustomerPaymentDTO>>> byCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer payments retrieved", paymentService.findByCustomer(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/customer/{customerId}/receivables")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> receivables(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer receivables", paymentService.receivables(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/customer/{customerId}/credit-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> creditSummary(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer credit summary", paymentService.creditSummary(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/sale/{saleId}")
    public ResponseEntity<ApiResponse<List<CustomerPaymentDTO>>> bySale(@PathVariable Integer saleId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale payments retrieved", paymentService.findBySale(saleId)));
    }
}
