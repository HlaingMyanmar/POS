package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPromoQuoteDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPromoValidateRequest;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPromoService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customer-portal")
public class CustomerPromoController {

    private final CustomerPromoService promos;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/promo-code/validate")
    public ResponseEntity<ApiResponse<CustomerPromoQuoteDTO>> validate(@RequestBody CustomerPromoValidateRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Promo", promos.preview(body)));
    }
}
