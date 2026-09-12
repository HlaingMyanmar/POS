package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryTownshipDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryWardDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerOrderDeliveryUpdateRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderDTO;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer-orders")
@RequiredArgsConstructor
public class CustomerOrderShopController {

    private final CustomerPortalService service;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerPortalOrderDTO>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer app orders", service.shopList()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<CustomerPortalOrderDTO>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Customer app order", service.shopOrder(id)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CustomerPortalOrderDTO>> status(
            @PathVariable Integer id, @RequestParam CustomerOrderStatus status) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.updateStatus(id, status)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PatchMapping("/{id}/delivery")
    public ResponseEntity<ApiResponse<CustomerPortalOrderDTO>> delivery(
            @PathVariable Integer id, @RequestBody CustomerOrderDeliveryUpdateRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Delivery updated", service.updateDelivery(id, body)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/townships")
    public ResponseEntity<ApiResponse<List<CustomerDeliveryTownshipDTO>>> townships() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Townships", service.shopTownships()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/townships")
    public ResponseEntity<ApiResponse<CustomerDeliveryTownshipDTO>> saveTownship(
            @RequestBody CustomerDeliveryTownshipDTO body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Township saved", service.saveTownship(body)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/wards")
    public ResponseEntity<ApiResponse<List<CustomerDeliveryWardDTO>>> wards() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Wards", service.shopWards()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/wards")
    public ResponseEntity<ApiResponse<CustomerDeliveryWardDTO>> saveWard(
            @RequestBody CustomerDeliveryWardDTO body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Ward saved", service.saveWard(body)));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryRegionDTO>>> regions() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Regions", service.shopRegions()));
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
    @PostMapping("/regions")
    public ResponseEntity<ApiResponse<org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryRegionDTO>> saveRegion(
            @RequestBody org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryRegionDTO body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Region saved", service.saveRegion(body)));
    }
}
