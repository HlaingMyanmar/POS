package org.sspd.servicemgmt.authoption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;
import org.sspd.servicemgmt.jwt.CustomUserDetailsService;
import org.sspd.servicemgmt.jwt.JwtService;
import org.sspd.servicemgmt.jwt.TokenAwareUserDetails;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceRefreshTest {
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshSessionRepository refreshSessionRepository = mock(RefreshSessionRepository.class);
    private final RefreshTokenHasher refreshTokenHasher = mock(RefreshTokenHasher.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private RefreshTokenReuseHandler refreshTokenReuseHandler;
    private AuthService service;

    @BeforeEach
    void setUp() {
        refreshTokenReuseHandler = new RefreshTokenReuseHandler(refreshSessionRepository, userRepository);
        service = new AuthService(
                authenticationManager,
                jwtService,
                userDetailsService,
                userRepository,
                refreshSessionRepository,
                refreshTokenHasher,
                loginAttemptService,
                auditLogService,
                refreshTokenReuseHandler);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "singleSessionPerUser", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "idleTimeoutMinutes", 20L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "absoluteTimeoutHours", 10L);
    }

    @Test
    void rotatesValidRefreshTokenAndRevokesPreviousSession() {
        var details = new TokenAwareUserDetails(
                "tech@example.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"),
                        new SimpleGrantedAuthority("CAN_ACCESS_SERVICE_JOB_READ")),
                4);
        var user = user(4L, 4);
        var session = activeSession(user.getId(), "jti-old", "family-1", "hash-old");

        when(jwtService.isRefreshToken("refresh-old")).thenReturn(true);
        when(jwtService.extractJti("refresh-old")).thenReturn("jti-old");
        when(refreshSessionRepository.findByJtiForUpdate("jti-old")).thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("refresh-old")).thenReturn("hash-old");
        when(jwtService.extractUsername("refresh-old")).thenReturn("tech@example.com");
        when(userDetailsService.loadUserByUsername("tech@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("refresh-old", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("refresh-old")).thenReturn(4);
        when(userRepository.findByUsernameOrEmail("tech@example.com", "tech@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtService.generateToken(details, 4)).thenReturn("access-new");
        when(jwtService.generateRefreshToken(eq(details), eq(4), anyString(), anyLong())).thenReturn("refresh-new");
        when(jwtService.extractExpiration("refresh-new")).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(refreshTokenHasher.hash("refresh-new")).thenReturn("hash-new");

        AuthService.LoginResult result = service.refresh("refresh-old");

        assertEquals("access-new", result.accessToken());
        assertEquals("refresh-new", result.refreshToken());
        assertTrue(result.roles().contains("ROLE_TECHNICIAN"));
        assertTrue(result.permissions().contains("CAN_ACCESS_SERVICE_JOB_READ"));
        assertNotNull(session.getRevokedAt());
        assertNotNull(session.getReplacedByJti());
        verify(refreshSessionRepository).save(argThat(saved ->
                "hash-new".equals(saved.getTokenHash())
                        && "family-1".equals(saved.getFamilyId())
                        && saved.getUserId().equals(4L)
                        && saved.getSessionStartedAt() != null
                        && saved.getLastSeenAt() != null));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsWhenIdleTimeoutExceeded() {
        var details = new TokenAwareUserDetails(
                "tech@example.com", "hash", true, List.of(), 4);
        var user = user(4L, 4);
        var session = activeSession(user.getId(), "jti-old", "family-1", "hash-old");
        session.setLastSeenAt(Instant.now().minusSeconds(21 * 60));

        when(jwtService.isRefreshToken("refresh-old")).thenReturn(true);
        when(jwtService.extractJti("refresh-old")).thenReturn("jti-old");
        when(refreshSessionRepository.findByJtiForUpdate("jti-old")).thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("refresh-old")).thenReturn("hash-old");
        when(jwtService.extractUsername("refresh-old")).thenReturn("tech@example.com");
        when(userDetailsService.loadUserByUsername("tech@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("refresh-old", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("refresh-old")).thenReturn(4);
        when(userRepository.findByUsernameOrEmail("tech@example.com", "tech@example.com"))
                .thenReturn(Optional.of(user));

        AuthSessionException ex = assertThrows(AuthSessionException.class, () -> service.refresh("refresh-old"));
        assertEquals(AuthSessionException.SESSION_IDLE, ex.getErrorCode());
        verify(refreshSessionRepository, never()).save(any());
    }

    @Test
    void rejectsWhenAbsoluteTimeoutExceeded() {
        var details = new TokenAwareUserDetails(
                "tech@example.com", "hash", true, List.of(), 4);
        var user = user(4L, 4);
        var session = activeSession(user.getId(), "jti-old", "family-1", "hash-old");
        session.setSessionStartedAt(Instant.now().minusSeconds(11 * 3600));
        session.setLastSeenAt(Instant.now());

        when(jwtService.isRefreshToken("refresh-old")).thenReturn(true);
        when(jwtService.extractJti("refresh-old")).thenReturn("jti-old");
        when(refreshSessionRepository.findByJtiForUpdate("jti-old")).thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("refresh-old")).thenReturn("hash-old");
        when(jwtService.extractUsername("refresh-old")).thenReturn("tech@example.com");
        when(userDetailsService.loadUserByUsername("tech@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("refresh-old", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("refresh-old")).thenReturn(4);
        when(userRepository.findByUsernameOrEmail("tech@example.com", "tech@example.com"))
                .thenReturn(Optional.of(user));

        AuthSessionException ex = assertThrows(AuthSessionException.class, () -> service.refresh("refresh-old"));
        assertEquals(AuthSessionException.SESSION_ABSOLUTE, ex.getErrorCode());
        verify(refreshSessionRepository).revokeAllActiveInFamily(eq("family-1"), any());
    }

    @Test
    void unlockResetsIdleWithinAbsoluteWindow() {
        var details = new TokenAwareUserDetails(
                "tech@example.com", "hash", true, List.of(), 4);
        var user = user(4L, 4);
        user.setUsername("tech");
        var session = activeSession(user.getId(), "jti-old", "family-1", "hash-old");
        session.setLastSeenAt(Instant.now().minusSeconds(30 * 60));

        when(jwtService.isRefreshToken("refresh-old")).thenReturn(true);
        when(jwtService.extractJti("refresh-old")).thenReturn("jti-old");
        when(refreshSessionRepository.findByJtiForUpdate("jti-old")).thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("refresh-old")).thenReturn("hash-old");
        when(jwtService.extractUsername("refresh-old")).thenReturn("tech@example.com");
        when(userDetailsService.loadUserByUsername("tech@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("refresh-old", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("refresh-old")).thenReturn(4);
        when(userRepository.findByUsernameOrEmail("tech@example.com", "tech@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtService.generateToken(details, 4)).thenReturn("access-new");
        when(jwtService.generateRefreshToken(eq(details), eq(4), anyString(), anyLong())).thenReturn("refresh-new");
        when(jwtService.extractExpiration("refresh-new")).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        when(refreshTokenHasher.hash("refresh-new")).thenReturn("hash-new");

        AuthService.LoginResult result = service.unlock("refresh-old", "secret");
        assertEquals("access-new", result.accessToken());
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void rejectsAccessTokenAtRefreshEndpoint() {
        when(jwtService.isRefreshToken("access-token")).thenReturn(false);
        assertThrows(BadCredentialsException.class, () -> service.refresh("access-token"));
        verifyNoInteractions(userDetailsService, userRepository, refreshSessionRepository);
    }

    @Test
    void rejectsRefreshTokenFromInvalidatedSession() {
        var details = new TokenAwareUserDetails(
                "tech@example.com", "hash", true, List.of(), 5);
        var session = activeSession(9L, "jti-old", "family-9", "hash-old");

        when(jwtService.isRefreshToken("old-refresh")).thenReturn(true);
        when(jwtService.extractJti("old-refresh")).thenReturn("jti-old");
        when(refreshSessionRepository.findByJtiForUpdate("jti-old")).thenReturn(Optional.of(session));
        when(refreshTokenHasher.hash("old-refresh")).thenReturn("hash-old");
        when(jwtService.extractUsername("old-refresh")).thenReturn("tech@example.com");
        when(userDetailsService.loadUserByUsername("tech@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("old-refresh", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("old-refresh")).thenReturn(4);

        assertThrows(BadCredentialsException.class, () -> service.refresh("old-refresh"));
        verify(refreshSessionRepository).revokeAllActiveInFamily(eq("family-9"), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void reuseOfRotatedRefreshTokenRevokesFamilyAndBumpsTokenVersion() {
        Instant now = Instant.now();
        var session = RefreshSession.builder()
                .id(1L)
                .userId(4L)
                .jti("jti-old")
                .tokenHash("hash-old")
                .familyId("family-1")
                .expiresAt(now.plusSeconds(3600))
                .revokedAt(now.minusSeconds(10))
                .replacedByJti("jti-new")
                .createdAt(now.minusSeconds(60))
                .sessionStartedAt(now.minusSeconds(60))
                .lastSeenAt(now.minusSeconds(60))
                .build();
        var user = user(4L, 4);

        when(jwtService.isRefreshToken("refresh-old")).thenReturn(true);
        when(jwtService.extractJti("refresh-old")).thenReturn("jti-old");
        when(refreshSessionRepository.findByJtiForUpdate("jti-old")).thenReturn(Optional.of(session));
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () -> service.refresh("refresh-old"));

        verify(refreshSessionRepository).revokeAllActiveInFamily(eq("family-1"), any());
        assertEquals(5, user.getTokenVersion());
        verify(userRepository).save(user);
    }

    @Test
    void logoutBumpsTokenVersionAndRevokesSessions() {
        var user = user(4L, 4);
        var session = activeSession(4L, "jti-1", "family-1", "hash-1");

        when(jwtService.isRefreshToken("refresh-1")).thenReturn(true);
        when(jwtService.extractUsername("refresh-1")).thenReturn("tech@example.com");
        when(userRepository.findByUsernameOrEmail("tech@example.com", "tech@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtService.extractJti("refresh-1")).thenReturn("jti-1");
        when(refreshSessionRepository.findByJtiForUpdate("jti-1")).thenReturn(Optional.of(session));

        service.logout("refresh-1", null);

        assertEquals(5, user.getTokenVersion());
        verify(refreshSessionRepository).revokeAllActiveInFamily(eq("family-1"), any());
        verify(refreshSessionRepository).revokeAllActiveForUser(eq(4L), any());
        verify(userRepository).save(user);
    }

    private static User user(Long id, int tokenVersion) {
        var user = new User();
        user.setId(id);
        user.setEmail("tech@example.com");
        user.setUsername("tech");
        user.setName("Tech");
        user.setPhone("09123");
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private static RefreshSession activeSession(Long userId, String jti, String familyId, String hash) {
        Instant now = Instant.now();
        return RefreshSession.builder()
                .id(1L)
                .userId(userId)
                .jti(jti)
                .tokenHash(hash)
                .familyId(familyId)
                .expiresAt(now.plusSeconds(3600))
                .createdAt(now)
                .sessionStartedAt(now)
                .lastSeenAt(now)
                .build();
    }
}
