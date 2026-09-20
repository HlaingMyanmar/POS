package org.sspd.servicemgmt.authoption;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.sspd.servicemgmt.jwt.CustomUserDetailsService;
import org.sspd.servicemgmt.jwt.JwtService;
import org.sspd.servicemgmt.jwt.TokenAwareUserDetails;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceRefreshTest {
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthService service =
            new AuthService(authenticationManager, jwtService, userDetailsService, userRepository);

    @Test
    void rotatesValidRefreshTokenWithoutChangingSessionVersion() {
        var details = new TokenAwareUserDetails(
                "tech@example.com", "hash", true,
                List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"),
                        new SimpleGrantedAuthority("CAN_ACCESS_SERVICE_JOB_READ")),
                4);
        var user = new User();
        user.setEmail("tech@example.com");
        user.setUsername("tech");
        user.setName("Tech");
        user.setPhone("09123");
        user.setTokenVersion(4);

        when(jwtService.isRefreshToken("refresh-old")).thenReturn(true);
        when(jwtService.extractUsername("refresh-old")).thenReturn("tech@example.com");
        when(userDetailsService.loadUserByUsername("tech@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("refresh-old", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("refresh-old")).thenReturn(4);
        when(userRepository.findByUsernameOrEmail("tech@example.com", "tech@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtService.generateToken(details, 4)).thenReturn("access-new");
        when(jwtService.generateRefreshToken(details, 4)).thenReturn("refresh-new");

        AuthService.LoginResult result = service.refresh("refresh-old");

        assertEquals("access-new", result.accessToken());
        assertEquals("refresh-new", result.refreshToken());
        assertTrue(result.roles().contains("ROLE_TECHNICIAN"));
        assertTrue(result.permissions().contains("CAN_ACCESS_SERVICE_JOB_READ"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAccessTokenAtRefreshEndpoint() {
        when(jwtService.isRefreshToken("access-token")).thenReturn(false);
        assertThrows(BadCredentialsException.class, () -> service.refresh("access-token"));
        verifyNoInteractions(userDetailsService, userRepository);
    }

    @Test
    void rejectsRefreshTokenFromInvalidatedSession() {
        var details = new TokenAwareUserDetails(
                "tech@example.com", "hash", true, List.of(), 5);
        when(jwtService.isRefreshToken("old-refresh")).thenReturn(true);
        when(jwtService.extractUsername("old-refresh")).thenReturn("tech@example.com");
        when(userDetailsService.loadUserByUsername("tech@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("old-refresh", details)).thenReturn(true);
        when(jwtService.extractTokenVersion("old-refresh")).thenReturn(4);

        assertThrows(BadCredentialsException.class, () -> service.refresh("old-refresh"));
        verifyNoInteractions(userRepository);
    }
}
