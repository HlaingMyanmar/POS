package com.sspd.servicemgmt.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class InactivityPolicyTest {
    private val startedAt = 1_000L

    @Test
    fun `does nothing before lock timeout`() {
        assertEquals(
            InactivityAction.NONE,
            InactivityPolicy.action(
                startedAt,
                startedAt + InactivityPolicy.LOCK_TIMEOUT_MILLIS - 1,
                lockEnabled = true
            )
        )
    }

    @Test
    fun `locks at lock timeout`() {
        assertEquals(
            InactivityAction.LOCK,
            InactivityPolicy.action(
                startedAt,
                startedAt + InactivityPolicy.LOCK_TIMEOUT_MILLIS,
                lockEnabled = true
            )
        )
    }

    @Test
    fun `logout takes precedence and cannot be disabled`() {
        assertEquals(
            InactivityAction.LOGOUT,
            InactivityPolicy.action(
                startedAt,
                startedAt + InactivityPolicy.LOGOUT_TIMEOUT_MILLIS,
                lockEnabled = false
            )
        )
    }

    @Test
    fun `clock rollback does not lock`() {
        assertEquals(
            InactivityAction.NONE,
            InactivityPolicy.action(startedAt, startedAt - 1, lockEnabled = true)
        )
    }
}
