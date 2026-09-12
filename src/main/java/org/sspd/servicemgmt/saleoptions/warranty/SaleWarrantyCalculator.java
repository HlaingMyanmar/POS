package org.sspd.servicemgmt.saleoptions.warranty;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Frozen customer-sale warranty dates. Never reread {@code Product.warrantyMonths}
 * for historical rows.
 */
public final class SaleWarrantyCalculator {

    private SaleWarrantyCalculator() {}

    public record Snapshot(int months, LocalDate startDate, LocalDate endDate) {
        public static Snapshot none() {
            return new Snapshot(0, null, null);
        }
    }

    public static Snapshot snapshot(Integer months, LocalDate saleDate) {
        int m = months == null ? 0 : months;
        if (m <= 0 || saleDate == null) return Snapshot.none();
        return new Snapshot(m, saleDate, saleDate.plusMonths(m));
    }

    /** ACTIVE on the expiry date; EXPIRED after; NONE when no warranty was sold. */
    public static String status(Integer months, LocalDate endDate, LocalDate today) {
        if (months == null || months <= 0 || endDate == null) return "NONE";
        LocalDate day = today == null ? LocalDate.now() : today;
        return day.isAfter(endDate) ? "EXPIRED" : "ACTIVE";
    }

    public static long daysRemaining(Integer months, LocalDate endDate, LocalDate today) {
        if (months == null || months <= 0 || endDate == null) return 0;
        LocalDate day = today == null ? LocalDate.now() : today;
        if (day.isAfter(endDate)) return 0;
        return ChronoUnit.DAYS.between(day, endDate);
    }
}
