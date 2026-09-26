package com.sspd.servicemgmt.feature.home

import com.sspd.servicemgmt.core.network.BookingSummary
import com.sspd.servicemgmt.core.network.CustomerJob
import java.util.Locale

private val CLOSED_BOOKING_STATUSES = setOf("CANCELED", "CANCELLED", "REJECTED", "DONE", "COMPLETED")
private val CLOSED_JOB_STATUSES = setOf("DELIVERED", "CANCELED", "CANCELLED", "REJECTED")

private fun String?.normalizedStatus(): String =
    this?.trim()?.uppercase(Locale.ROOT).orEmpty()

internal fun isActiveBooking(booking: BookingSummary): Boolean =
    booking.status.normalizedStatus() !in CLOSED_BOOKING_STATUSES

internal fun isActiveCustomerJob(job: CustomerJob): Boolean =
    job.status.normalizedStatus() !in CLOSED_JOB_STATUSES

internal fun activeServiceCount(
    jobs: List<CustomerJob>,
    bookings: List<BookingSummary>
): Int = jobs.count(::isActiveCustomerJob) + bookings.count(::isActiveBooking)
