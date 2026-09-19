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

import java.util.Optional;

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

        setRefreshCookie(response, result.refreshToken());
        return authResponse(result, "Login Successful");
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refreshToken", required = false) String refreshCookie,
            HttpServletResponse response) {
        String refreshToken = Optional.ofNullable(request)
                .map(RefreshTokenRequest::refreshToken)
                .filter(token -> !token.isBlank())
                .orElse(refreshCookie);
        AuthService.LoginResult result = authService.refresh(refreshToken);
        setRefreshCookie(response, result.refreshToken());
        return authResponse(result, "Token refreshed");
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        ResponseCookie expired = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged out", null));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> authResponse(
            AuthService.LoginResult result, String message) {
        AuthResponse authResponse = new AuthResponse(
                result.accessToken(), result.refreshToken(), result.username(), result.name(), result.phone(),
                result.staffId(), result.roles(), result.permissions());
        return ResponseEntity.ok(new ApiResponse<>(true, message, authResponse));
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}