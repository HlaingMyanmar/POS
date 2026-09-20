package com.sspd.servicemgmt.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityTest {
    @Test
    fun `PIN uses salted PBKDF2 and verifies`() {
        val first = PinHasher.hash(charArrayOf('1', '2', '3', '4'))
        val second = PinHasher.hash(charArrayOf('1', '2', '3', '4'))

        assertTrue(first.startsWith("pbkdf2_sha256$${PinHasher.ITERATIONS}$"))
        assertFalse(first.contains("1234"))
        assertNotEquals(first, second)
        assertTrue(PinHasher.verify(charArrayOf('1', '2', '3', '4'), first))
        assertFalse(PinHasher.verify(charArrayOf('4', '3', '2', '1'), first))
    }

    @Test
    fun `fifth failure starts persistent lockout`() {
        val now = 10_000L
        var state = PinThrottleState()
        repeat(PinThrottlePolicy.MAX_ATTEMPTS) {
            state = PinThrottlePolicy.afterFailure(state, now)
        }

        assertEquals(now + PinThrottlePolicy.LOCKOUT_MILLIS, state.lockedUntilMillis)
        assertTrue(PinThrottlePolicy.beforeAttempt(state, now) is PinAttemptDecision.Locked)
        assertEquals(
            PinAttemptDecision.Allowed,
            PinThrottlePolicy.beforeAttempt(state, state.lockedUntilMillis)
        )
    }

    @Test
    fun `attempt budget counts down before lockout`() {
        val oneFailure = PinThrottlePolicy.afterFailure(PinThrottleState(), 1L)
        assertEquals(1, oneFailure.failedAttempts)
        assertEquals(4, PinThrottlePolicy.remainingAttempts(oneFailure))
    }
}
