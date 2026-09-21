package org.sspd.servicemgmt.bookingoptions.repository;

/** Aggregate item counts for booking list/summary responses (avoids N+1 lazy loads). */
public interface BookingItemSummaryProjection {
    Integer getBookingId();

    long getItemCount();

    long getUnconvertedCount();
}
