package org.sspd.servicemgmt.customerportaloptions.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.*;
import org.sspd.servicemgmt.customerportaloptions.service.*;

@RestController @RequiredArgsConstructor
public class DeliveryPricingController {
 private final DeliveryPricingService pricing;
 private final CustomerOrderPaymentService payments;
 private final CustomerPortalService portal;
 @GetMapping("/api/v1/customer-orders/delivery-policy")
 @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
 public ApiResponse<DeliveryPricingService.Policy> policy(){return new ApiResponse<>(true,"Policy",pricing.policy());}
 @PutMapping("/api/v1/customer-orders/delivery-policy")
 @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
 public ApiResponse<DeliveryPricingService.Policy> savePolicy(@RequestBody DeliveryPricingService.Policy p){pricing.savePolicy(p);return policy();}
 @GetMapping("/api/v1/products/{id}/shipping")
 @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
 public ApiResponse<DeliveryPricingService.Profile> profile(@PathVariable int id){return new ApiResponse<>(true,"Shipping profile",pricing.profile(id));}
 @PutMapping("/api/v1/products/{id}/shipping")
 @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
 public ApiResponse<DeliveryPricingService.Profile> profile(@PathVariable int id,@RequestBody DeliveryPricingService.Profile p){
  pricing.saveProfile(id,p);return new ApiResponse<>(true,"Saved",p);
 }
 @PostMapping("/api/v1/customer-portal/delivery-quote")
 @PreAuthorize("hasRole('CUSTOMER')")
 public ApiResponse<DeliveryPricingService.Quote> quote(@RequestBody CustomerPortalOrderRequest r){
  return new ApiResponse<>(true,"Delivery estimate",pricing.quote(r));
 }
 @PostMapping("/api/v1/customer-orders/{id}/shipping-quote")
 @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
 public ApiResponse<CustomerPortalOrderDTO> shopQuote(@PathVariable int id,@RequestBody CustomerOrderPaymentService.ShippingDecision r){
  payments.quoteShipping(id,r);return new ApiResponse<>(true,"Quote sent",portal.shopOrder(id));
 }
 @PostMapping("/api/v1/customer-portal/orders/{id}/shipping-decision")
 @PreAuthorize("hasRole('CUSTOMER')")
 public ApiResponse<CustomerPortalOrderDTO> decide(@PathVariable int id,@RequestBody CustomerOrderPaymentService.ShippingDecision r){
  payments.decideShipping(id,r);return new ApiResponse<>(true,"Updated",portal.shopOrder(id));
 }
}
