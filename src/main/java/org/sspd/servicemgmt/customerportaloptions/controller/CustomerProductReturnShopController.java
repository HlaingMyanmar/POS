package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingModerateRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerProductReturnDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerProductReturnReviewRequest;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderRatingService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerProductReturnService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customer-order-returns")
public class CustomerProductReturnShopController {

    private final CustomerProductReturnService returns;
    private final CustomerOrderRatingService ratings;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerProductReturnDTO>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer returns", returns.shopList()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerProductReturnDTO>> get(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "false") boolean photos) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer return", returns.shopGet(id, photos)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/{id}/review")
    public ResponseEntity<ApiResponse<CustomerProductReturnDTO>> review(
            @PathVariable Integer id,
            @RequestBody CustomerProductReturnReviewRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", returns.review(id, body)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/ratings")
    public ResponseEntity<ApiResponse<List<CustomerOrderRatingDTO>>> ratings() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Ratings", ratings.shopList()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/ratings/{id}/moderate")
    public ResponseEntity<ApiResponse<CustomerOrderRatingDTO>> moderate(
            @PathVariable Integer id,
            @RequestBody CustomerOrderRatingModerateRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", ratings.moderate(id, body)));
    }
}
