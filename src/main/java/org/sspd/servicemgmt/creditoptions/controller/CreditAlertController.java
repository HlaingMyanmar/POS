package org.sspd.servicemgmt.creditoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.creditoptions.dto.CreditAlertDTO;
import org.sspd.servicemgmt.creditoptions.service.CreditAlertService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/credit-alerts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CreditAlertController {

    private final CreditAlertService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CreditAlertDTO>>> all() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Alerts retrieved", service.listUnresolved()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<CreditAlertDTO>>> byCustomer(@PathVariable Integer customerId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Alerts retrieved", service.listByCustomer(customerId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<ApiResponse<CreditAlertDTO>> resolve(@PathVariable Integer alertId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Alert resolved", service.resolve(alertId)));
    }
}
