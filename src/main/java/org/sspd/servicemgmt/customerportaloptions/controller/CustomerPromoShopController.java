package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPromoCodeDTO;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPromoService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customer-promo-codes")
public class CustomerPromoShopController {

    private final CustomerPromoService promos;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerPromoCodeDTO>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Promos", promos.shopList()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerPromoCodeDTO>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Promo", promos.shopGet(id)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerPromoCodeDTO>> save(@RequestBody CustomerPromoCodeDTO body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", promos.save(body)));
    }
}
