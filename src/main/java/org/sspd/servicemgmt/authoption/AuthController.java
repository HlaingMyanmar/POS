package org.sspd.servicemgmt.authoption;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody AuthRequest request,
            HttpServletResponse response,
            HttpServletRequest httpRequest) {

        // ၁. AuthService မှ Login ရလဒ်ကို ယူပါ
        AuthService.LoginResult result = authService.authenticateUser(request);

        // Login audit log
        try {
            String ip = getClientIp(httpRequest);
            String ua = httpRequest.getHeader("User-Agent");
            String device = (ua != null && (ua.contains("okhttp") || ua.contains("Expo") || ua.contains("ReactNative"))) ? "MOBILE" : "WEB";
            String role = result.roles().stream().findFirst().map(r -> r.replace("ROLE_","")).orElse("");
            auditLogService.log(result.username(), role, "LOGIN", "Auth", null, "User logged in", ip, device);
        } catch (Exception ignored) {}

        // ၂. Refresh Token ကို HttpOnly Cookie အဖြစ် သတ်မှတ်ပါ
        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(true)    // Production မှာဆိုရင် true (HTTPS) ပေးရပါမယ်
                .path("/")
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .sameSite("Lax")
                .build();

        // Header မှာ Cookie ထည့်လိုက်ခြင်း
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // ၃. Frontend JSON body အတွက် AuthResponse ကို ပြန်ပေးခြင်း
        AuthResponse authResponse = new AuthResponse(
                result.accessToken(),
                result.username(),
                result.name(),
                result.phone(),
                result.roles(),
                result.permissions()
        );

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Login Successful", authResponse)
        );
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}