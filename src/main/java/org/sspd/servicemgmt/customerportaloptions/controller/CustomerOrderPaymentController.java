package org.sspd.servicemgmt.customerportaloptions.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.*;
import org.sspd.servicemgmt.customerportaloptions.service.*;
import org.sspd.servicemgmt.customerportaloptions.repository.*;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequiredArgsConstructor
public class CustomerOrderPaymentController {
 private final CustomerOrderPaymentService payments;
 private final CustomerPortalService portal;
 private final CustomerOrderPaymentProofRepository proofs;
 private final CustomerOrderRepository orders;
 private ApiResponse<CustomerPortalOrderDTO> result(Integer id) {return new ApiResponse<>(true,"Updated",portal.shopOrder(id));}
 @PostMapping("/api/v1/customer-orders/{id}/reserve")
 @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_UPDATE','CAN_ACCESS_SALE_UPDATE')")
 public ApiResponse<CustomerPortalOrderDTO> reserve(@PathVariable Integer id,@RequestBody(required=false) OrderPaymentRequest request){
  payments.reserve(id, request==null?new OrderPaymentRequest():request);return result(id);
 }
 @PostMapping("/api/v1/customer-orders/{id}/payment-review")
 @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_UPDATE')")
 public ApiResponse<CustomerPortalOrderDTO> review(@PathVariable Integer id,@RequestBody OrderPaymentRequest request){payments.review(id,request);return result(id);}
 @PostMapping("/api/v1/customer-orders/{id}/fulfill")
 @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_CREATE')")
 public ApiResponse<CustomerPortalOrderDTO> fulfill(@PathVariable Integer id,@RequestBody OrderPaymentRequest request){payments.fulfill(id,request);return result(id);}

 @GetMapping("/api/v1/customer-portal/payment-channels")
 @PreAuthorize("hasRole('CUSTOMER')")
 public ApiResponse<List<CustomerPaymentChannelDTO>> channels() {
  return new ApiResponse<>(true, "Payment channels", payments.customerChannels());
 }

 @PostMapping("/api/v1/customer-portal/orders/{id}/payment-choice")
 @PreAuthorize("hasRole('CUSTOMER')")
 public ApiResponse<CustomerPortalOrderDTO> choosePayment(@PathVariable Integer id, @RequestBody OrderPaymentRequest request) {
  payments.choosePayment(id, request.getPaymentChoice());
  return result(id);
 }

 @PostMapping("/api/v1/customer-portal/orders/{id}/payment-channel")
 @PreAuthorize("hasRole('CUSTOMER')")
 public ApiResponse<CustomerPortalOrderDTO> chooseChannel(@PathVariable Integer id, @RequestBody OrderPaymentRequest request) {
  payments.chooseChannel(id, request.getPaymentMethodId());
  return result(id);
 }

 @PostMapping(value="/api/v1/customer-portal/orders/{id}/payment-proof",consumes="multipart/form-data")
 @PreAuthorize("hasRole('CUSTOMER')")
 public ApiResponse<CustomerPortalOrderDTO> submit(
   @PathVariable Integer id,
   @RequestParam(required=false) String reference,
   @RequestParam BigDecimal amount,
   @RequestParam(required=false) Integer paymentMethodId,
   @RequestPart("image") MultipartFile image) throws java.io.IOException {
  payments.submit(id,reference,amount,image,paymentMethodId);return result(id);
 }
 @GetMapping("/api/v1/customer-orders/{id}/payment-proof")
 @PreAuthorize("hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
 public ApiResponse<Map<String,Object>> proof(@PathVariable Integer id) {
  var order=orders.findById(id).orElseThrow();
  Integer proofId=Set.of("REMAINDER_PROOF_SUBMITTED","REMAINDER_CHECKING").contains(order.getPaymentState())
   ? order.getCollectionProofId() : order.getLatestProofId();
  if(proofId==null)return new ApiResponse<>(true,"No proof",Map.of());
  var p=proofs.findById(proofId).orElseThrow();
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("id",p.getId());result.put("reference",p.getTransactionReference());result.put("amount",p.getAmount());result.put("submittedAt",p.getSubmittedAt());
  result.put("image", "data:"+p.getImageType()+";base64,"+Base64.getEncoder().encodeToString(p.getImageData()));
  result.put("reviewState",p.getReviewState());result.put("reviewedBy",p.getReviewedBy());result.put("reviewNote",p.getReviewNote());
  return new ApiResponse<>(true,"Payment proof",result);
 }

 @GetMapping({"/api/v1/customer-orders/{id}/payment-proofs","/api/v1/customer-portal/orders/{id}/payment-proofs"})
 @PreAuthorize("hasRole('CUSTOMER') or hasAnyAuthority('CAN_ACCESS_CUSTOMER_APP_ORDER_READ','CAN_ACCESS_SALE_READ')")
 public ApiResponse<Map<String,Object>> proofHistory(@PathVariable Integer id) {
  var order=orders.findById(id).orElseThrow();
  var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null && auth.getPrincipal() instanceof org.sspd.servicemgmt.jwt.CustomerPortalUserDetails customer
    && !order.getCustomer().getId().equals(customer.getCustomerId()))
   throw new org.springframework.security.access.AccessDeniedException("This order belongs to another customer");
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("deposit",proofMap(order.getLatestProofId()));
  result.put("remainder",proofMap(order.getCollectionProofId()));
  return new ApiResponse<>(true,"Payment proofs",result);
 }

 private Map<String,Object> proofMap(Integer proofId) {
  if(proofId==null)return null;
  var p=proofs.findById(proofId).orElse(null);if(p==null)return null;
  Map<String,Object> result=new LinkedHashMap<>();
  result.put("id",p.getId());result.put("reference",p.getTransactionReference());result.put("amount",p.getAmount());result.put("submittedAt",p.getSubmittedAt());
  result.put("image","data:"+p.getImageType()+";base64,"+Base64.getEncoder().encodeToString(p.getImageData()));
  result.put("reviewState",p.getReviewState());result.put("reviewedBy",p.getReviewedBy());result.put("reviewNote",p.getReviewNote());
  return result;
 }
}
