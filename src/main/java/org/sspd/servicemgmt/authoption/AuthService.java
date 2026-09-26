package org.sspd.servicemgmt.authoption;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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

    @Value("${application.security.auth.single-session-per-user:true}")
    private boolean singleSessionPerUser;

    @Value("${application.security.auth.idle-timeout-minutes:20}")
    private long idleTimeoutMinutes;

    @Value("${application.security.auth.absolute-timeout-hours:10}")
    private long absoluteTimeoutHours;

    @Transactional
    public LoginResult authenticateUser(AuthRequest request, String ip, String device) {
        String rawLogin = request.getUsernameOremail() == null ? "" : request.getUsernameOremail().trim();
        if (rawLogin.isBlank()) {
            throw new BadCredentialsException("Username သို့မဟုတ် Password မှားနေပါသည်");
        }

        Optional<User> knownUser = userRepository.findByUsernameOrEmail(rawLogin, rawLogin);
        String lockKey = knownUser
                .map(u -> LoginAttemptService.canonicalUserKey(u.getId()))
                .orElseGet(() -> LoginAttemptService.normalizeKey(rawLogin));

        loginAttemptService.assertNotLocked(lockKey);
        knownUser.ifPresent(u -> {
            loginAttemptService.assertNotLocked(LoginAttemptService.normalizeKey(u.getUsername()));
            loginAttemptService.assertNotLocked(LoginAttemptService.normalizeKey(u.getEmail()));
        });

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(rawLogin, request.getPassword())
            );
        } catch (LockedException ex) {
            auditFailedLogin(rawLogin, ip, device, "Account locked");
            throw ex;
        } catch (AuthenticationException ex) {
            boolean lockedNow = loginAttemptService.recordFailure(lockKey);
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
                lockKey,
                LoginAttemptService.canonicalUserKey(user.getId()),
                LoginAttemptService.normalizeKey(rawLogin),
                LoginAttemptService.normalizeKey(user.getUsername()),
                LoginAttemptService.normalizeKey(user.getEmail()));

        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (singleSessionPerUser) {
            tokenVersion = tokenVersion + 1;
            user.setTokenVersion(tokenVersion);
            userRepository.save(user);
            refreshSessionRepository.revokeAllActiveForUser(user.getId(), Instant.now());
        }

        Instant now = Instant.now();
        UserDetails userDetails = userDetailsService.loadUserByUsername(rawLogin);
        String accessToken = jwtService.generateToken(userDetails, tokenVersion);
        String refreshJti = UUID.randomUUID().toString();
        long remainingMs = absoluteTimeout().toMillis();
        String refreshToken = jwtService.generateRefreshToken(userDetails, tokenVersion, refreshJti, remainingMs);
        persistRefreshSession(user.getId(), refreshToken, refreshJti, UUID.randomUUID().toString(), now, now);

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
        return rotateRefreshSession(refreshToken, false);
    }

    /**
     * Idle unlock: password re-check + refresh cookie. Resets idle clock; absolute clock unchanged.
     */
    @Transactional
    public LoginResult unlock(String refreshToken, String password) {
        if (password == null || password.isBlank()) {
            throw new BadCredentialsException("Password is required");
        }
        RefreshSessionProbe probe = loadActiveRefreshSession(refreshToken);
        assertAbsoluteNotExpired(probe.session(), Instant.now());

        User user = probe.user();
        String login = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername()
                : user.getEmail();
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(login, password));
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Username သို့မဟုတ် Password မှားနေပါသည်");
        }

        return rotateRefreshSession(refreshToken, true);
    }

    /**
     * Issues a short-lived step-up token after password confirmation (for void / refund / RBAC).
     */
    @Transactional(readOnly = true)
    public StepUpResult issueStepUp(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BadCredentialsException("Password is required");
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Username သို့မဟုတ် Password မှားနေပါသည်");
        }
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        int tokenVersion = userDetails instanceof TokenAwareUserDetails tad ? tad.getTokenVersion() : 0;
        String stepUpToken = jwtService.generateStepUpToken(userDetails, tokenVersion);
        return new StepUpResult(stepUpToken, jwtService.getStepUpExpirationMs() / 1000L);
    }

    private LoginResult rotateRefreshSession(String refreshToken, boolean unlockIdle) {
        try {
            RefreshSessionProbe probe = loadActiveRefreshSession(refreshToken);
            Instant now = Instant.now();
            RefreshSession session = probe.session();
            UserDetails userDetails = probe.userDetails();
            User user = probe.user();
            TokenAwareUserDetails tokenAware = (TokenAwareUserDetails) userDetails;

            assertAbsoluteNotExpired(session, now);
            if (!unlockIdle) {
                assertIdleNotExpired(session, now);
            }

            int tokenVersion = tokenAware.getTokenVersion();
            String accessToken = jwtService.generateToken(userDetails, tokenVersion);
            String newJti = UUID.randomUUID().toString();
            Instant sessionStarted = session.effectiveSessionStartedAt();
            long remainingMs = remainingAbsoluteMs(sessionStarted, now);
            String rotatedRefreshToken = jwtService.generateRefreshToken(
                    userDetails, tokenVersion, newJti, remainingMs);

            session.setRevokedAt(now);
            session.setReplacedByJti(newJti);
            persistRefreshSession(
                    user.getId(),
                    rotatedRefreshToken,
                    newJti,
                    session.getFamilyId(),
                    sessionStarted,
                    now);

            return toLoginResult(userDetails, user, accessToken, rotatedRefreshToken);
        } catch (AuthSessionException | BadCredentialsException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
    }

    private RefreshSessionProbe loadActiveRefreshSession(String refreshToken) {
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

        return new RefreshSessionProbe(session, userDetails, user);
    }

    private void assertAbsoluteNotExpired(RefreshSession session, Instant now) {
        Instant deadline = session.effectiveSessionStartedAt().plus(absoluteTimeout());
        if (!deadline.isAfter(now)) {
            refreshTokenReuseHandler.revokeFamily(session.getFamilyId());
            throw new AuthSessionException(
                    AuthSessionException.SESSION_ABSOLUTE,
                    "Session expired. Please sign in again.");
        }
    }

    private void assertIdleNotExpired(RefreshSession session, Instant now) {
        Instant lastSeen = session.effectiveLastSeenAt();
        if (!lastSeen.plus(idleTimeout()).isAfter(now)) {
            throw new AuthSessionException(
                    AuthSessionException.SESSION_IDLE,
                    "Session locked due to inactivity. Please unlock.");
        }
    }

    private Duration idleTimeout() {
        return Duration.ofMinutes(Math.max(1, idleTimeoutMinutes));
    }

    private Duration absoluteTimeout() {
        return Duration.ofHours(Math.max(1, absoluteTimeoutHours));
    }

    private long remainingAbsoluteMs(Instant sessionStartedAt, Instant now) {
        Instant deadline = sessionStartedAt.plus(absoluteTimeout());
        long remaining = Duration.between(now, deadline).toMillis();
        return Math.max(1_000L, remaining);
    }

    /**
     * Revokes the caller's refresh family (when known).
     * When {@code single-session-per-user} is enabled, also revokes all other devices and bumps
     * {@code tokenVersion} so outstanding access tokens fail immediately.
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
                // Still invalidate below when configured for single-session.
            }
        }

        if (singleSessionPerUser) {
            refreshSessionRepository.revokeAllActiveForUser(user.getId(), now);
            int nextVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
            user.setTokenVersion(nextVersion);
            userRepository.save(user);
        }
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

    private void persistRefreshSession(
            Long userId,
            String refreshToken,
            String jti,
            String familyId,
            Instant sessionStartedAt,
            Instant lastSeenAt) {
        Instant expiresAt = jwtService.extractExpiration(refreshToken).toInstant();
        RefreshSession session = RefreshSession.builder()
                .userId(userId)
                .jti(jti)
                .tokenHash(refreshTokenHasher.hash(refreshToken))
                .familyId(familyId)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .sessionStartedAt(sessionStartedAt)
                .lastSeenAt(lastSeenAt)
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

    private record RefreshSessionProbe(RefreshSession session, UserDetails userDetails, User user) {}

    public record LoginResult(
            String accessToken,
            String refreshToken,
            String username,
            String name,
            String phone,
            Integer staffId,
            Set<String> roles,
            Set<String> permissions) {}

    public record StepUpResult(String stepUpToken, long expiresInSeconds) {}
}
