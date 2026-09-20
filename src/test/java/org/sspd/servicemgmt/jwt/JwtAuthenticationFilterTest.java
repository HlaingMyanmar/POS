package org.sspd.servicemgmt.jwt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private static final String SECRET =
            "dGVzdC1qd3Qtc2VjcmV0LWF0LWxlYXN0LTMyLWJ5dGVzISE=";
    private static final String OTHER_SECRET =
            "YW5vdGhlci10ZXN0LWp3dC1zZWNyZXQtMzItYnl0ZXMhIQ==";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void malformedInvalidSignatureAndExpiredTokensReturnControlled401() throws Exception {
        JwtService validIssuer = new JwtService(SECRET, 60_000);
        JwtService otherIssuer = new JwtService(OTHER_SECRET, 60_000);
        JwtService expiredIssuer = new JwtService(SECRET, -1);
        var details = details(2);

        assertControlled401(validIssuer, "not-a-jwt");
        assertControlled401(validIssuer, otherIssuer.generateToken(details, 2));
        assertControlled401(validIssuer, expiredIssuer.generateToken(details, 2));
    }

    @Test
    void refreshTokenCannotAuthenticateAsAccessToken() throws Exception {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        assertControlled401(jwtService, jwtService.generateRefreshToken(details(2), 2));
    }

    @Test
    void validAccessTokenAuthenticatesAndContinuesChain() throws Exception {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        CustomUserDetailsService users = mock(CustomUserDetailsService.class);
        TokenAwareUserDetails details = details(2);
        when(users.loadUserByUsername("staff@example.com")).thenReturn(details);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users);
        MockHttpServletRequest request = request(jwtService.generateToken(details, 2));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals("staff@example.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private static void assertControlled401(JwtService jwtService, String token) throws Exception {
        CustomUserDetailsService users = mock(CustomUserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(token), response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("TOKEN_EXPIRED_OR_INVALID"));
        assertNull(chain.getRequest());
        verifyNoInteractions(users);
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static TokenAwareUserDetails details(int version) {
        return new TokenAwareUserDetails(
                "staff@example.com",
                "hash",
                true,
                List.of(new SimpleGrantedAuthority("CAN_ACCESS_PRODUCT_READ")),
                version
        );
    }
}
