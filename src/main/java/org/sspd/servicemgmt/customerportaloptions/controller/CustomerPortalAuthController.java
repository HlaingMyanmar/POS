package org.sspd.servicemgmt.customerportaloptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalAuthResponse;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalForgotRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalGoogleLoginRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalLoginRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalPublicConfigDTO;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalRegisterRequest;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerPortalResetRequest;
import org.sspd.servicemgmt.customerportaloptions.service.CustomerPortalAuthService;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1/customer-portal/auth")
@RequiredArgsConstructor
public class CustomerPortalAuthController {

    private final CustomerPortalAuthService authService;

    @Value("${app.customer-portal.google.client-ids:}")
    private String googleClientIds;

    @GetMapping("/public-config")
    public ResponseEntity<ApiResponse<CustomerPortalPublicConfigDTO>> publicConfig() {
        String clientId = java.util.Arrays.stream(googleClientIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("");
        return ResponseEntity.ok(new ApiResponse<>(true, "OK",
                new CustomerPortalPublicConfigDTO(clientId, !clientId.isBlank())));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> register(@RequestBody CustomerPortalRegisterRequest request) {
        CustomerPortalAuthResponse data = authService.register(request);
        if (data.isResetSent()) {
            return ResponseEntity.ok(new ApiResponse<>(true,
                    "ဤ email ဖြင့် အကောင့်ရှိပြီးသား။ Password ပြန်သတ်မှတ်ရန် link ကို email သို့ ပို့ပြီးပါပြီ", data));
        }
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Registered", data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> login(@RequestBody CustomerPortalLoginRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged in", authService.login(request)));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> google(@RequestBody CustomerPortalGoogleLoginRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged in", authService.loginWithGoogle(request)));
    }

    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> forgot(@RequestBody CustomerPortalForgotRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Password ပြန်သတ်မှတ်ရန် link ကို email သို့ ပို့ပြီးပါပြီ", authService.forgotPassword(request)));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<CustomerPortalAuthResponse>> reset(@RequestBody CustomerPortalResetRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Password ပြောင်းပြီးပါပြီ", authService.resetPassword(request)));
    }
}
