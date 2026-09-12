package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogProductDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerWishlistRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerWishlistStatusDTO;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerWishlistService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customer-portal/wishlist")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerWishlistController {

    private final CustomerWishlistService wishlists;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerCatalogProductDTO>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Wishlist", wishlists.list()));
    }

    @GetMapping("/ids")
    public ResponseEntity<ApiResponse<List<Integer>>> ids() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Wishlist ids", wishlists.ids()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerWishlistStatusDTO>> toggle(@RequestBody CustomerWishlistRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", wishlists.toggle(body == null ? null : body.getProductId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<CustomerWishlistStatusDTO>> add(@RequestBody CustomerWishlistRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", wishlists.add(body == null ? null : body.getProductId())));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<CustomerWishlistStatusDTO>> remove(@PathVariable Integer productId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Removed", wishlists.remove(productId)));
    }
}
