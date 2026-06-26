package org.sspd.servicemgmt.creditoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.creditoptions.dto.CustomerCreditTermDTO;
import org.sspd.servicemgmt.creditoptions.service.CustomerCreditTermService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/credit-terms")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CreditTermController {

    private final CustomerCreditTermService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerCreditTermDTO>>> all() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Credit terms retrieved", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<CustomerCreditTermDTO>> byCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Credit term found", service.findByCustomer(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerCreditTermDTO>> save(@Valid @RequestBody CustomerCreditTermDTO dto) {
        return ResponseEntity.status(201)
                .body(new ApiResponse<>(true, "Credit term saved", service.saveOrUpdate(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
    @PutMapping
    public ResponseEntity<ApiResponse<CustomerCreditTermDTO>> update(@Valid @RequestBody CustomerCreditTermDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Credit term updated", service.saveOrUpdate(dto)));
    }
}
