package org.sspd.servicemgmt.saleoptions.warranty;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Frozen customer-sale warranty dates. Never reread {@code Product.warrantyMonths}
 * for historical rows after snapshot.
 */
public final class SaleWarrantyCalculator {

    private static final Pattern TERMS = Pattern.compile(
            "^(\\d+)\\s*(ရက်|နေ့|days?|d|နှစ်|years?|y|လ|months?|m)\\b",
            Pattern.CASE_INSENSITIVE);

    private SaleWarrantyCalculator() {}

    public record Snapshot(int months, LocalDate startDate, LocalDate endDate) {
        public static Snapshot none() {
            return new Snapshot(0, null, null);
        }

        public boolean hasCoverage() {
            return endDate != null;
        }
    }

    public static Snapshot snapshot(Integer months, LocalDate saleDate) {
        return snapshot(months, null, saleDate);
    }

    public static Snapshot snapshot(Integer months, String terms, LocalDate saleDate) {
        int m = months == null ? 0 : months;
        if (m > 0 && saleDate != null) {
            return new Snapshot(m, saleDate, saleDate.plusMonths(m));
        }
        return snapshotFromTerms(terms, saleDate);
    }

    public static Snapshot fromExisting(Integer months, LocalDate startDate, LocalDate endDate) {
        int m = months == null ? 0 : months;
        if (endDate != null) {
            return new Snapshot(m, startDate != null ? startDate : endDate, endDate);
        }
        if (m > 0 && startDate != null) {
            return new Snapshot(m, startDate, startDate.plusMonths(m));
        }
        return Snapshot.none();
    }

    public static Snapshot snapshotFromTerms(String terms, LocalDate saleDate) {
        if (saleDate == null || terms == null || terms.isBlank()) return Snapshot.none();
        Matcher match = TERMS.matcher(terms.trim());
        if (!match.find()) return Snapshot.none();
        int value = Integer.parseInt(match.group(1));
        if (value <= 0) return Snapshot.none();
        String unit = match.group(2).toLowerCase();
        if (unit.equals("ရက်") || unit.equals("နေ့") || unit.startsWith("d")) {
            return new Snapshot(0, saleDate, saleDate.plusDays(value));
        }
        if (unit.equals("နှစ်") || unit.startsWith("y")) {
            return new Snapshot(value * 12, saleDate, saleDate.plusMonths(value * 12L));
        }
        return new Snapshot(value, saleDate, saleDate.plusMonths(value));
    }

    /** ACTIVE on the expiry date; EXPIRED after; NONE when no warranty was sold. */
    public static String status(Integer months, LocalDate endDate, LocalDate today) {
        if (endDate == null) return "NONE";
        LocalDate day = today == null ? LocalDate.now() : today;
        return day.isAfter(endDate) ? "EXPIRED" : "ACTIVE";
    }

    public static long daysRemaining(Integer months, LocalDate endDate, LocalDate today) {
        if (endDate == null) return 0;
        LocalDate day = today == null ? LocalDate.now() : today;
        if (day.isAfter(endDate)) return 0;
        return ChronoUnit.DAYS.between(day, endDate);
    }

    public static String durationLabel(Integer months, LocalDate startDate, LocalDate endDate) {
        int m = months == null ? 0 : months;
        if (m > 0) {
            return m % 12 == 0 ? (m / 12) + " နှစ်" : m + " လ";
        }
        if (startDate != null && endDate != null) {
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days > 0) return days + " ရက်";
        }
        return "";
    }
}
