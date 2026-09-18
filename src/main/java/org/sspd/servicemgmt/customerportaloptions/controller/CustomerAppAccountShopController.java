package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountAdminResult;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountEmailRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppAccountLinkRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerAppActivityDTO;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerAppAccountAdminService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer-app-accounts")
@RequiredArgsConstructor
public class CustomerAppAccountShopController {

    private final CustomerPortalService service;
    private final CustomerAppAccountAdminService adminService;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerAppAccountDTO>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer app accounts", service.shopAccounts()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/{id}/activity")
    public ResponseEntity<ApiResponse<List<CustomerAppActivityDTO>>> activity(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer app activity", service.shopAccountActivity(id)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<ApiResponse<CustomerAppAccountDTO>> enabled(
            @PathVariable Integer id, @RequestParam boolean enabled) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.setAccountEnabled(id, enabled)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PatchMapping("/{id}/email")
    public ResponseEntity<ApiResponse<CustomerAppAccountDTO>> updateEmail(
            @PathVariable Integer id, @RequestBody CustomerAppAccountEmailRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Email updated",
                service.updateAccountEmail(id, body == null ? null : body.getEmail())));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerAppAccountAdminResult>> create(
            @RequestBody CustomerAppAccountLinkRequest body) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "App အကောင့် ဖန်တီးပြီး",
                adminService.createForCustomer(body)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/{id}/link")
    public ResponseEntity<ApiResponse<CustomerAppAccountAdminResult>> link(
            @PathVariable Integer id, @RequestBody CustomerAppAccountLinkRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "POS ဖောက်သည်နှင့် ချိတ်ပြီး",
                adminService.linkToCustomer(id, body)));
    }
}
