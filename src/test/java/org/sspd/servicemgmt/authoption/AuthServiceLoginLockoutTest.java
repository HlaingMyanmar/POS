package org.sspd.servicemgmt.authoption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;
import org.sspd.servicemgmt.jwt.CustomUserDetailsService;
import org.sspd.servicemgmt.jwt.JwtService;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceLoginLockoutTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshSessionRepository refreshSessionRepository = mock(RefreshSessionRepository.class);
    private final RefreshTokenHasher refreshTokenHasher = mock(RefreshTokenHasher.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RefreshTokenReuseHandler refreshTokenReuseHandler = mock(RefreshTokenReuseHandler.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void failedLoginIsAuditedAndCounted() {
        AuthRequest request = new AuthRequest("alice", "wrong");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.recordFailure("alice")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> service.authenticateUser(request, "203.0.113.10", "WEB"));

        verify(loginAttemptService).assertNotLocked("alice");
        verify(loginAttemptService).recordFailure("alice");
        verify(auditLogService).log(eq("alice"), eq(""), eq("LOGIN_FAILED"), eq("Auth"),
                isNull(), contains("Failed login"), eq("203.0.113.10"), eq("WEB"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void lockoutThresholdThrowsLockedException() {
        AuthRequest request = new AuthRequest("alice", "wrong");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));
        when(loginAttemptService.recordFailure("alice")).thenReturn(true);

        assertThrows(LockedException.class,
                () -> service.authenticateUser(request, "203.0.113.10", "WEB"));

        verify(auditLogService).log(eq("alice"), eq(""), eq("LOGIN_FAILED"), eq("Auth"),
                isNull(), contains("locked"), eq("203.0.113.10"), eq("WEB"));
    }

    @Test
    void alreadyLockedAccountIsRejectedBeforeAuthenticate() {
        AuthRequest request = new AuthRequest("alice", "password1");
        doThrow(new LockedException("locked")).when(loginAttemptService).assertNotLocked("alice");

        assertThrows(LockedException.class,
                () -> service.authenticateUser(request, "203.0.113.10", "WEB"));

        verify(authenticationManager, never()).authenticate(any());
    }
}
