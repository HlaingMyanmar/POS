package org.sspd.servicemgmt.authoption;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.jwt.JwtService;

import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    public static final String CLIENT_TYPE_HEADER = "X-Client-Type";

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletResponse response,
            HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        boolean mobile = isMobileClient(httpRequest);
        String device = mobile ? "MOBILE" : "WEB";

        AuthService.LoginResult result = authService.authenticateUser(request, ip, device);
        return issueAuthResponse(result, "Login Successful", mobile, response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refreshToken", required = false) String refreshCookie,
            HttpServletResponse response,
            HttpServletRequest httpRequest) {
        String bodyToken = Optional.ofNullable(request)
                .map(RefreshTokenRequest::refreshToken)
                .filter(token -> !token.isBlank())
                .orElse(null);
        boolean mobile = bodyToken != null || isMobileClient(httpRequest);
        String refreshToken = bodyToken != null ? bodyToken : refreshCookie;

        AuthService.LoginResult result = authService.refresh(refreshToken);
        return issueAuthResponse(result, "Token refreshed", mobile, response);
    }

    /** Idle unlock — password + refresh cookie; absolute session window unchanged. */
    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<AuthResponse>> unlock(
            @Valid @RequestBody UnlockRequest request,
            @CookieValue(name = "refreshToken", required = false) String refreshCookie,
            HttpServletResponse response,
            HttpServletRequest httpRequest) {
        String bodyToken = request.refreshToken() != null && !request.refreshToken().isBlank()
                ? request.refreshToken()
                : null;
        boolean mobile = bodyToken != null || isMobileClient(httpRequest);
        String refreshToken = bodyToken != null ? bodyToken : refreshCookie;

        AuthService.LoginResult result = authService.unlock(refreshToken, request.password());
        return issueAuthResponse(result, "Session unlocked", mobile, response);
    }

    /** Short-lived token for void / refund / permission mutations. Requires authenticated access JWT. */
    @PostMapping("/step-up")
    public ResponseEntity<ApiResponse<StepUpResponse>> stepUp(@Valid @RequestBody StepUpRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new AuthSessionException(
                    AuthSessionException.STEP_UP_REQUIRED, "Authentication required");
        }
        AuthService.StepUpResult result = authService.issueStepUp(authentication.getName(), request.password());
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Step-up granted",
                new StepUpResponse(result.stepUpToken(), result.expiresInSeconds())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refreshToken", required = false) String refreshCookie,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            HttpServletResponse response) {
        String refreshToken = Optional.ofNullable(request)
                .map(RefreshTokenRequest::refreshToken)
                .filter(token -> !token.isBlank())
                .orElse(refreshCookie);
        String accessToken = extractBearer(authorization);
        authService.logout(refreshToken, accessToken);

        clearRefreshCookie(response);
        return ResponseEntity.ok(new ApiResponse<>(true, "Logged out", null));
    }

    /**
     * Web: HttpOnly cookie only (refresh token never appears in JSON).
     * Mobile: refresh token in response body (cookie clients cannot use cookies reliably).
     */
    private ResponseEntity<ApiResponse<AuthResponse>> issueAuthResponse(
            AuthService.LoginResult result,
            String message,
            boolean mobile,
            HttpServletResponse response) {
        if (mobile) {
            AuthResponse body = toAuthResponse(result, result.refreshToken());
            return ResponseEntity.ok(new ApiResponse<>(true, message, body));
        }
        setRefreshCookie(response, result.refreshToken());
        AuthResponse body = toAuthResponse(result, null);
        return ResponseEntity.ok(new ApiResponse<>(true, message, body));
    }

    private static AuthResponse toAuthResponse(AuthService.LoginResult result, String refreshToken) {
        return new AuthResponse(
                result.accessToken(),
                refreshToken,
                result.username(),
                result.name(),
                result.phone(),
                result.staffId(),
                result.roles(),
                result.permissions());
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        long maxAgeSeconds = Math.max(1L, jwtService.remainingSeconds(refreshToken));
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie expired = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
    }

    /**
     * Prefer {@code X-Client-Type: mobile|web}. Fall back to known mobile User-Agents for older apps.
     */
    static boolean isMobileClient(HttpServletRequest request) {
        String clientType = request.getHeader(CLIENT_TYPE_HEADER);
        if (clientType != null && !clientType.isBlank()) {
            String normalized = clientType.trim().toLowerCase(Locale.ROOT);
            if ("mobile".equals(normalized)) {
                return true;
            }
            if ("web".equals(normalized)) {
                return false;
            }
        }
        String ua = request.getHeader("User-Agent");
        return ua != null && (ua.contains("okhttp") || ua.contains("Expo") || ua.contains("ReactNative"));
    }

    private String extractBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}
