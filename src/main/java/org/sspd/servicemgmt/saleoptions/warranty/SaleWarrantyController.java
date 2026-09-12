package org.sspd.servicemgmt.saleoptions.warranty;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.sspd.servicemgmt.api.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sale-warranties")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SaleWarrantyController {

    private final SaleWarrantyQueryService service;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_CUSTOMER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SaleWarrantyDTO>>> search(
            @RequestParam(required = false) Integer saleId,
            @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) String serial,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Warranties",
                service.search(saleId, customerId, serial, status)));
    }
}
