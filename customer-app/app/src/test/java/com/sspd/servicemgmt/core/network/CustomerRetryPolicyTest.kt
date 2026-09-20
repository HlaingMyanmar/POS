package com.sspd.servicemgmt.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerRetryPolicyTest {
    private val token = "Bearer valid-access-token"

    @Test
    fun `protected api unauthorized response ends session`() {
        assertTrue(
            CustomerRetryPolicy.shouldEndSessionOnUnauthorized(
                token,
                "/api/v1/customer-portal/orders"
            )
        )
    }

    @Test
    fun `login and websocket failures do not trigger global logout`() {
        assertFalse(
            CustomerRetryPolicy.shouldEndSessionOnUnauthorized(token, "/api/v1/customer-portal/auth/login")
        )
        assertFalse(
            CustomerRetryPolicy.shouldEndSessionOnUnauthorized(token, "/ws-native")
        )
    }

    @Test
    fun `anonymous or malformed authorization does not end session`() {
        assertFalse(CustomerRetryPolicy.shouldEndSessionOnUnauthorized(null, "/api/v1/orders"))
        assertFalse(CustomerRetryPolicy.shouldEndSessionOnUnauthorized("Bearer short", "/api/v1/orders"))
    }
}
