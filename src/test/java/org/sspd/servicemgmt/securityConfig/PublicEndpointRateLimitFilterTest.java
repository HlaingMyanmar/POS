package org.sspd.servicemgmt.securityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class PublicEndpointRateLimitFilterTest {

    @Test
    void limitsStaffLoginPerClientAddress() throws Exception {
        PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            assertEquals(200, invoke(filter, "POST", "/api/v1/auth/login", "203.0.113.10").getStatus());
        }
        MockHttpServletResponse blocked =
                invoke(filter, "POST", "/api/v1/auth/login", "203.0.113.10");
        assertEquals(429, blocked.getStatus());
        assertEquals("10", blocked.getHeader("X-RateLimit-Limit"));
        assertNotNull(blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("Too many requests"));

        assertEquals(200,
                invoke(filter, "POST", "/api/v1/auth/login", "203.0.113.11").getStatus());
    }

    @Test
    void forgotPasswordHasStricterLimit() throws Exception {
        PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter();
        String path = "/api/v1/customer-portal/auth/forgot";

        for (int i = 0; i < 3; i++) {
            assertEquals(200, invoke(filter, "POST", path, "198.51.100.20").getStatus());
        }
        assertEquals(429, invoke(filter, "POST", path, "198.51.100.20").getStatus());
    }

    @Test
    void forgedForwardedForHeaderDoesNotChangeClientBucket() throws Exception {
        PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.10");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            request.addHeader("Forwarded", "for=198.51.100." + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedRequest.setRemoteAddr("203.0.113.10");
        blockedRequest.addHeader("X-Forwarded-For", "198.51.100.99");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(blockedRequest, blocked, new MockFilterChain());
        assertEquals(429, blocked.getStatus());
    }

    @Test
    void unrelatedAndNonPostRequestsAreNotLimited() throws Exception {
        PublicEndpointRateLimitFilter filter = new PublicEndpointRateLimitFilter();
        for (int i = 0; i < 20; i++) {
            assertEquals(200,
                    invoke(filter, "GET", "/api/v1/auth/login", "192.0.2.1").getStatus());
            assertEquals(200,
                    invoke(filter, "POST", "/api/v1/products", "192.0.2.1").getStatus());
        }
    }

    private static MockHttpServletResponse invoke(
            PublicEndpointRateLimitFilter filter,
            String method,
            String path,
            String remoteAddress
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
