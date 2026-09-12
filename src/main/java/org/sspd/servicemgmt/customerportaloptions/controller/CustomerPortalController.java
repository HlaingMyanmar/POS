package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.bookingoptions.dto.BookingDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogOptionDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogProductDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogServiceDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalAuthResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalBookingRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalOrderRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalPasswordChangeRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalProfileRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerReceiptConfirmRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalPurchaseDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalJobDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalBrandingDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalNotificationDTO;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalAuthService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalInvoiceService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerLoyaltyService;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerChatService;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer-portal")
@RequiredArgsConstructor
public class CustomerPortalController {

    private final CustomerPortalService service;
    private final CustomerPortalAuthService authService;
    private final CompanySettingsService companySettingsService;
    private final CustomerPortalInvoiceService invoiceService;
    private final CustomerLoyaltyService loyaltyService;
    private final CustomerChatService chatService;

    @GetMapping("/branding")
    public ResponseEntity<ApiResponse<CustomerPortalBrandingDTO>> branding() {
        var s = companySettingsService.getSettings();
        boolean hasLogo = s.getLogoBase64() != null && !s.getLogoBase64().isBlank();
        // Do not embed large base64 in JSON — app loads /branding/logo instead (reliable on mobile).
        return ResponseEntity.ok(new ApiResponse<>(true, "Branding", CustomerPortalBrandingDTO.builder()
                .companyName(s.getCompanyName())
                .taglineMm(s.getTaglineMm())
                .logoBase64(null)
                .hasLogo(hasLogo)
                .logoUrl(hasLogo ? "/api/v1/customer-portal/branding/logo" : null)
                .pickupDepositPercent(s.getPickupDepositPercent() != null
                        ? s.getPickupDepositPercent()
                        : new java.math.BigDecimal("30.00"))
                .deliveryEnabled(service.deliveryEnabled())
                .build()));
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<byte[]> brandingLogo() {
        var s = companySettingsService.getSettings();
        String raw = s.getLogoBase64();
        if (raw == null || raw.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try {
            String payload = raw.trim();
            org.springframework.http.MediaType mediaType = org.springframework.http.MediaType.IMAGE_PNG;
            if (payload.regionMatches(true, 0, "data:", 0, 5)) {
                int comma = payload.indexOf(',');
                if (comma < 0) return ResponseEntity.badRequest().build();
                String meta = payload.substring(5, comma).toLowerCase();
                payload = payload.substring(comma + 1);
                if (meta.contains("image/jpeg") || meta.contains("image/jpg")) {
                    mediaType = org.springframework.http.MediaType.IMAGE_JPEG;
                } else if (meta.contains("image/webp")) {
                    mediaType = org.springframework.http.MediaType.parseMediaType("image/webp");
                } else if (meta.contains("image/gif")) {
                    mediaType = org.springframework.http.MediaType.IMAGE_GIF;
                }
            }
            byte[] bytes = java.util.Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
            if (bytes.length == 0) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
                    .header("Content-Disposition", "inline; filename=\"company-logo\"")
                    .body(bytes);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/catalog/products")
    public ResponseEntity<ApiResponse<List<CustomerCatalogProductDTO>>> products() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Products", service.catalogProducts()));
    }

    @GetMapping("/catalog/products/page")
    public ResponseEntity<ApiResponse<org.sspd.servicemgmt.customerportaloptions.dto.CustomerCatalogPageDTO>> productPage(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId, @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "name") String sort) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Products", service.catalogPage(page, size, q, categoryId, brandId, productType, sort)));
    }

    @GetMapping("/catalog/categories")
    public ResponseEntity<ApiResponse<List<CustomerCatalogOptionDTO>>> categories() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Categories", service.catalogCategories()));
    }

    @GetMapping("/catalog/brands")
    public ResponseEntity<ApiResponse<List<CustomerCatalogOptionDTO>>> brands() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Brands", service.catalogBrands()));
    }

    @GetMapping("/catalog/services")
    public ResponseEntity<ApiResponse<List<CustomerCatalogServiceDTO>>> services() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Services", service.catalogServices()));
    }

    @GetMapping("/delivery-townships")
    public ResponseEntity<ApiResponse<List<org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryTownshipDTO>>> deliveryTownships() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Delivery townships", service.activeTownships()));
    }

    @GetMapping("/delivery-locations")
    public ResponseEntity<ApiResponse<List<org.sspd.servicemgmt.customerportaloptions.dto.CustomerDeliveryRegionDTO>>> deliveryLocations() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Delivery locations", service.activeDeliveryLocations()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> me() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile", authService.me()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/me/loyalty")
    public ResponseEntity<ApiResponse<org.sspd.servicemgmt.customerportaloptions.dto.CustomerLoyaltyDTO>> loyalty() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Loyalty points", loyaltyService.mine()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/chat/history")
    public ResponseEntity<ApiResponse<List<org.sspd.servicemgmt.customerportaloptions.dto.CustomerChatDTO>>> chatHistory() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Messages", chatService.history()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/chat/send")
    public ResponseEntity<ApiResponse<org.sspd.servicemgmt.customerportaloptions.dto.CustomerChatDTO>> sendChat(
            @RequestBody org.sspd.servicemgmt.customerportaloptions.dto.CustomerChatRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sent", chatService.send(request == null ? null : request.text())));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> completeProfile(@RequestBody CustomerPortalProfileRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile updated", authService.completeProfile(request)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> changePassword(
            @RequestBody CustomerPortalPasswordChangeRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Password changed", authService.changePassword(request)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/bookings")
    public ResponseEntity<ApiResponse<BookingDTO>> requestService(@RequestBody CustomerPortalBookingRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Service requested", service.requestService(request)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<List<BookingDTO>>> myBookings() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Bookings", service.myBookings()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<CustomerPortalOrderDTO>> placeOrder(@RequestBody CustomerPortalOrderRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Order placed", service.placeOrder(request)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<CustomerPortalOrderDTO>>> myOrders() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Orders", service.myOrders()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<CustomerPortalOrderDTO>> myOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Order", service.myOrder(id)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<ApiResponse<CustomerPortalOrderDTO>> cancelOrder(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cancelled", service.customerCancel(id)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/orders/{id}/receipt")
    public ResponseEntity<ApiResponse<CustomerPortalOrderDTO>> confirmReceipt(
            @PathVariable Integer id,
            @RequestBody CustomerReceiptConfirmRequest body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.confirmReceipt(id, body)));
    }

    /** Official POS sale voucher PDF (same template as shop print). */
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping(value = "/orders/{id}/invoice.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> orderInvoicePdf(
            @PathVariable Integer id,
            @RequestParam(required = false) String paper) {
        return invoiceService.orderInvoicePdf(id, paper);
    }

    /** Payment received confirmation PDF (available after staff APPROVE; before sale fulfill). */
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping(value = "/orders/{id}/payment-receipt.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> paymentReceiptPdf(@PathVariable Integer id) {
        return invoiceService.paymentReceiptPdf(id);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping(value = "/history/purchases/{saleId}/invoice.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> purchaseInvoicePdf(
            @PathVariable Integer saleId,
            @RequestParam(required = false) String paper) {
        return invoiceService.purchaseInvoicePdf(saleId, paper);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/history/purchases")
    public ResponseEntity<ApiResponse<List<CustomerPortalPurchaseDTO>>> myPurchases() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchases", service.myPurchases()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/history/warranties")
    public ResponseEntity<ApiResponse<List<org.sspd.servicemgmt.saleoptions.warranty.SaleWarrantyDTO>>> myWarranties() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Warranties", service.myWarranties()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/history/jobs")
    public ResponseEntity<ApiResponse<List<CustomerPortalJobDTO>>> myJobs() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service jobs", service.myJobs()));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<CustomerPortalNotificationDTO>>> myNotifications() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Notifications", service.myNotifications()));
    }
}
