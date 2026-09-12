package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderRatingRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerProductReturnDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerProductReturnRequest;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerOrderRatingService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerProductReturnService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customer-portal")
public class CustomerProductReturnController {

    private final CustomerProductReturnService returns;
    private final CustomerOrderRatingService ratings;

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(value = "/orders/{id}/returns")
    public ResponseEntity<ApiResponse<CustomerProductReturnDTO>> submit(
            @PathVariable Integer id,
            @RequestBody CustomerProductReturnRequest body) throws java.io.IOException {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Return requested", returns.submit(id, body, List.of())));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(value = "/orders/{id}/returns/photos", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CustomerProductReturnDTO>> submitWithPhotos(
            @PathVariable Integer id,
            @RequestPart("body") CustomerProductReturnRequest body,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) throws java.io.IOException {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Return requested", returns.submit(id, body, photos)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping(value = "/returns/{id}/photos", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CustomerProductReturnDTO>> addPhotos(
            @PathVariable Integer id,
            @RequestPart("photos") List<MultipartFile> photos) throws java.io.IOException {
        return ResponseEntity.ok(new ApiResponse<>(true, "Photos saved", returns.addPhotos(id, photos)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/returns")
    public ResponseEntity<ApiResponse<List<CustomerProductReturnDTO>>> mine() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Returns", returns.mine()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders/{id}/returns")
    public ResponseEntity<ApiResponse<List<CustomerProductReturnDTO>>> forOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Returns", returns.forOrder(id)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/orders/{id}/rate")
    public ResponseEntity<ApiResponse<CustomerOrderRatingDTO>> rate(
            @PathVariable Integer id,
            @RequestBody CustomerOrderRatingRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rated", ratings.rate(id, body)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/purchases/{saleId}/rate")
    public ResponseEntity<ApiResponse<CustomerOrderRatingDTO>> rateSale(
            @PathVariable Integer saleId,
            @RequestBody CustomerOrderRatingRequest body) {
        body.setSaleId(saleId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Rated", ratings.rateSale(saleId, body)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders/{id}/rating")
    public ResponseEntity<ApiResponse<CustomerOrderRatingDTO>> orderRating(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rating", ratings.forOrder(id)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/ratings")
    public ResponseEntity<ApiResponse<List<CustomerOrderRatingDTO>>> myRatings() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Ratings", ratings.mine()));
    }
}
