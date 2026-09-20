package org.sspd.servicemgmt.authoption;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;
import org.sspd.servicemgmt.jwt.CustomUserDetailsService;
import org.sspd.servicemgmt.jwt.JwtService;
import org.sspd.servicemgmt.jwt.TokenAwareUserDetails;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final RefreshTokenHasher refreshTokenHasher;
    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;
    private final RefreshTokenReuseHandler refreshTokenReuseHandler;

    @Transactional
    public LoginResult authenticateUser(AuthRequest request, String ip, String device) {
        String rawLogin = request.getUsernameOremail() == null ? "" : request.getUsernameOremail().trim();
        if (rawLogin.isBlank()) {
            throw new BadCredentialsException("Username သို့မဟုတ် Password မှားနေပါသည်");
        }
        String loginKey = LoginAttemptService.normalizeKey(rawLogin);

        loginAttemptService.assertNotLocked(loginKey);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(rawLogin, request.getPassword())
            );
        } catch (LockedException ex) {
            auditFailedLogin(rawLogin, ip, device, "Account locked");
            throw ex;
        } catch (AuthenticationException ex) {
            boolean lockedNow = loginAttemptService.recordFailure(loginKey);
            auditFailedLogin(rawLogin, ip, device,
                    lockedNow
                            ? "Failed login; account locked after repeated attempts"
                            : "Failed login");
            if (lockedNow) {
                throw new LockedException(
                        "Account temporarily locked due to failed login attempts. Try again later.");
            }
            throw new BadCredentialsException("Username သို့မဟုတ် Password မှားနေပါသည်");
        }

        User user = userRepository.findByUsernameOrEmail(rawLogin, rawLogin)
                .orElseThrow(() -> new BadCredentialsException("Username သို့မဟုတ် Password မှားနေပါသည်"));

        loginAttemptService.clear(
                loginKey,
                LoginAttemptService.normalizeKey(user.getUsername()),
                LoginAttemptService.normalizeKey(user.getEmail()));

        int newVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);
        refreshSessionRepository.revokeAllActiveForUser(user.getId(), Instant.now());

        UserDetails userDetails = userDetailsService.loadUserByUsername(rawLogin);
        String accessToken = jwtService.generateToken(userDetails, newVersion);
        String refreshJti = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(userDetails, newVersion, refreshJti);
        persistRefreshSession(user.getId(), refreshToken, refreshJti, UUID.randomUUID().toString());

        LoginResult result = toLoginResult(userDetails, user, accessToken, refreshToken);
        try {
            String role = result.roles().stream().findFirst().map(r -> r.replace("ROLE_", "")).orElse("");
            auditLogService.log(result.username(), role, "LOGIN", "Auth", null, "User logged in", ip, device);
        } catch (Exception ignored) {
        }
        return result;
    }

    private void auditFailedLogin(String actor, String ip, String device, String description) {
        try {
            auditLogService.log(actor, "", "LOGIN_FAILED", "Auth", null, description, ip, device);
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public LoginResult refresh(String refreshToken) {
        try {
            if (refreshToken == null || refreshToken.isBlank() || !jwtService.isRefreshToken(refreshToken)) {
                throw new BadCredentialsException("Invalid refresh token");
            }

            String jti = jwtService.extractJti(refreshToken);
            if (jti == null || jti.isBlank()) {
                throw new BadCredentialsException("Invalid refresh token");
            }

            RefreshSession session = refreshSessionRepository.findByJtiForUpdate(jti)
                    .orElseThrow(() -> new BadCredentialsException("Refresh session expired"));

            Instant now = Instant.now();
            if (!session.isActive(now)) {
                if (session.getReplacedByJti() != null) {
                    refreshTokenReuseHandler.revokeFamilyAndInvalidateUser(
                            session.getFamilyId(), session.getUserId());
                }
                throw new BadCredentialsException("Refresh session expired");
            }

            String expectedHash = refreshTokenHasher.hash(refreshToken);
            if (!Objects.equals(expectedHash, session.getTokenHash())) {
                refreshTokenReuseHandler.revokeFamilyAndInvalidateUser(
                        session.getFamilyId(), session.getUserId());
                throw new BadCredentialsException("Refresh session expired");
            }

            String username = jwtService.extractUsername(refreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!userDetails.isEnabled()
                    || !jwtService.isTokenValid(refreshToken, userDetails)
                    || !(userDetails instanceof TokenAwareUserDetails tokenAware)
                    || !Objects.equals(jwtService.extractTokenVersion(refreshToken), tokenAware.getTokenVersion())) {
                refreshTokenReuseHandler.revokeFamily(session.getFamilyId());
                throw new BadCredentialsException("Refresh session expired");
            }

            User user = userRepository.findByUsernameOrEmail(username, username)
                    .orElseThrow(() -> new BadCredentialsException("Refresh session expired"));
            if (!Objects.equals(user.getId(), session.getUserId())) {
                refreshTokenReuseHandler.revokeFamilyAndInvalidateUser(
                        session.getFamilyId(), session.getUserId());
                throw new BadCredentialsException("Refresh session expired");
            }

            int tokenVersion = tokenAware.getTokenVersion();
            String accessToken = jwtService.generateToken(userDetails, tokenVersion);
            String newJti = UUID.randomUUID().toString();
            String rotatedRefreshToken = jwtService.generateRefreshToken(userDetails, tokenVersion, newJti);

            session.setRevokedAt(now);
            session.setReplacedByJti(newJti);
            persistRefreshSession(user.getId(), rotatedRefreshToken, newJti, session.getFamilyId());

            return toLoginResult(userDetails, user, accessToken, rotatedRefreshToken);
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
    }

    /**
     * Revokes the caller's refresh family (when known) and bumps {@code tokenVersion}
     * so outstanding access tokens fail the JWT filter immediately.
     */
    @Transactional
    public void logout(String refreshToken, String accessToken) {
        Instant now = Instant.now();
        User user = resolveUserForLogout(refreshToken, accessToken);
        if (user == null) {
            return;
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                if (jwtService.isRefreshToken(refreshToken)) {
                    String jti = jwtService.extractJti(refreshToken);
                    if (jti != null && !jti.isBlank()) {
                        refreshSessionRepository.findByJtiForUpdate(jti).ifPresent(session ->
                                refreshSessionRepository.revokeAllActiveInFamily(session.getFamilyId(), now));
                    }
                }
            } catch (RuntimeException ignored) {
                // Still invalidate access tokens below when user was resolved.
            }
        }

        refreshSessionRepository.revokeAllActiveForUser(user.getId(), now);
        int nextVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        user.setTokenVersion(nextVersion);
        userRepository.save(user);
    }

    private User resolveUserForLogout(String refreshToken, String accessToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                if (jwtService.isRefreshToken(refreshToken)) {
                    String username = jwtService.extractUsername(refreshToken);
                    return userRepository.findByUsernameOrEmail(username, username).orElse(null);
                }
            } catch (RuntimeException ignored) {
                // Fall through to access token.
            }
        }
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                if (jwtService.isAccessToken(accessToken)) {
                    String username = jwtService.extractUsername(accessToken);
                    return userRepository.findByUsernameOrEmail(username, username).orElse(null);
                }
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private void persistRefreshSession(Long userId, String refreshToken, String jti, String familyId) {
        Instant expiresAt = jwtService.extractExpiration(refreshToken).toInstant();
        RefreshSession session = RefreshSession.builder()
                .userId(userId)
                .jti(jti)
                .tokenHash(refreshTokenHasher.hash(refreshToken))
                .familyId(familyId)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();
        refreshSessionRepository.save(session);
    }

    private LoginResult toLoginResult(
            UserDetails userDetails, User user, String accessToken, String refreshToken) {
        Set<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        Set<String> permissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        return new LoginResult(
                accessToken,
                refreshToken,
                userDetails.getUsername(),
                user.getName(),
                user.getPhone(),
                user.getStaff() != null ? user.getStaff().getId() : null,
                roles,
                permissions);
    }

    public record LoginResult(
            String accessToken,
            String refreshToken,
            String username,
            String name,
            String phone,
            Integer staffId,
            Set<String> roles,
            Set<String> permissions) {}
}
