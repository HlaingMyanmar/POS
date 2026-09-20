package com.sspd.servicemgmt.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerAuthPolicyTest {
    @Test
    fun `idle timeout requires an existing timestamped session`() {
        assertFalse(CustomerAuthPolicy.isIdle(false, 1_000L, 20_000L, 10_000L))
        assertFalse(CustomerAuthPolicy.isIdle(true, 0L, 20_000L, 10_000L))
        assertFalse(CustomerAuthPolicy.isIdle(true, 1_000L, 10_999L, 10_000L))
        assertTrue(CustomerAuthPolicy.isIdle(true, 1_000L, 11_000L, 10_000L))
    }

    @Test
    fun `startup action protects idle and biometric sessions`() {
        assertEquals(
            SessionStartAction.SHOW_LOGIN,
            CustomerAuthPolicy.startupAction(false, idle = false, biometricEnabled = false)
        )
        assertEquals(
            SessionStartAction.LOGOUT_IDLE_SESSION,
            CustomerAuthPolicy.startupAction(true, idle = true, biometricEnabled = true)
        )
        assertEquals(
            SessionStartAction.REQUIRE_BIOMETRIC,
            CustomerAuthPolicy.startupAction(true, idle = false, biometricEnabled = true)
        )
        assertEquals(
            SessionStartAction.OPEN_SESSION,
            CustomerAuthPolicy.startupAction(true, idle = false, biometricEnabled = false)
        )
    }
}
