package com.sspd.servicemgmt.feature.home

import com.sspd.servicemgmt.core.network.BookingSummary
import com.sspd.servicemgmt.core.network.CustomerJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceActivityPolicyTest {
    @Test
    fun `canceled and rejected bookings are excluded from active services`() {
        val bookings = listOf(
            BookingSummary(status = "CONFIRMED"),
            BookingSummary(status = "ARRIVED"),
            BookingSummary(status = "CANCELED"),
            BookingSummary(status = "CANCELLED"),
            BookingSummary(status = "REJECTED"),
            BookingSummary(status = " rejected ")
        )

        assertEquals(2, activeServiceCount(emptyList(), bookings))
    }

    @Test
    fun `terminal job statuses are excluded case insensitively`() {
        val jobs = listOf(
            CustomerJob(status = "IN_PROGRESS"),
            CustomerJob(status = "COMPLETED"),
            CustomerJob(status = "delivered"),
            CustomerJob(status = "CANCELED"),
            CustomerJob(status = "CANCELLED"),
            CustomerJob(status = "REJECTED")
        )

        assertEquals(2, activeServiceCount(jobs, emptyList()))
    }

    @Test
    fun `booking activity policy keeps open statuses and closes rejected statuses`() {
        assertTrue(isActiveBooking(BookingSummary(status = "CONFIRMED")))
        assertTrue(isActiveBooking(BookingSummary(status = "ARRIVED")))
        assertFalse(isActiveBooking(BookingSummary(status = "REJECTED")))
    }
}
