package com.sspd.servicemgmt.core.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPolicyTest {
    @Test
    fun `uses slower updates while technician is on site`() {
        assertEquals(45_000L, TrackingPolicy.updateIntervalMillis(onSite = false))
        assertEquals(180_000L, TrackingPolicy.updateIntervalMillis(onSite = true))
    }

    @Test
    fun `flushes first fix and throttles subsequent heartbeat`() {
        assertTrue(TrackingPolicy.shouldFlush(lastHeartbeatAtMillis = 0L, nowMillis = 1_000L))
        assertFalse(TrackingPolicy.shouldFlush(lastHeartbeatAtMillis = 1_000L, nowMillis = 20_999L))
        assertTrue(TrackingPolicy.shouldFlush(lastHeartbeatAtMillis = 1_000L, nowMillis = 21_000L))
    }
}
