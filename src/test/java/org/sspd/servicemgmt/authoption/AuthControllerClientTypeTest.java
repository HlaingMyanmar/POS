package org.sspd.servicemgmt.authoption;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerClientTypeTest {

    @Test
    void prefersExplicitClientTypeHeaderOverUserAgent() {
        MockHttpServletRequest web = new MockHttpServletRequest();
        web.addHeader(AuthController.CLIENT_TYPE_HEADER, "web");
        web.addHeader("User-Agent", "okhttp/4.12.0");
        assertFalse(AuthController.isMobileClient(web));

        MockHttpServletRequest mobile = new MockHttpServletRequest();
        mobile.addHeader(AuthController.CLIENT_TYPE_HEADER, "mobile");
        mobile.addHeader("User-Agent", "Mozilla/5.0");
        assertTrue(AuthController.isMobileClient(mobile));
    }

    @Test
    void fallsBackToKnownMobileUserAgents() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "okhttp/4.12.0");
        assertTrue(AuthController.isMobileClient(request));
    }
}
