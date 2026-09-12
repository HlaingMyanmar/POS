package org.sspd.servicemgmt.customerportaloptions.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPushTokenRequest;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerFcmService;
@RestController @RequestMapping("/api/v1/customer-portal/push-devices") @RequiredArgsConstructor @PreAuthorize("hasRole('CUSTOMER')")
public class CustomerPushController {
 private final CustomerFcmService push;
 @PostMapping public ResponseEntity<ApiResponse<Void>> register(@RequestBody CustomerPushTokenRequest request){push.register(request);return ResponseEntity.ok(new ApiResponse<>(true,"Push device registered",null));}
 @PostMapping("/unregister") public ResponseEntity<ApiResponse<Void>> unregister(@RequestBody CustomerPushTokenRequest request){push.unregister(request);return ResponseEntity.ok(new ApiResponse<>(true,"Push device unregistered",null));}
}
