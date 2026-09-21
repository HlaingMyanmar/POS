package org.sspd.servicemgmt.bookingoptions.support;

import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;

import java.math.BigDecimal;

/** Derives customer-facing catalog price semantics for booking snapshots. */
public final class ServicePriceSnapshotSupport {

    public static final String FIXED = "FIXED";
    public static final String STARTING_FROM = "STARTING_FROM";
    public static final String INSPECTION_REQUIRED = "INSPECTION_REQUIRED";

    public static final String ESTIMATE_NOT_REQUIRED = "NOT_REQUIRED";
    public static final String ESTIMATE_PENDING = "PENDING";
    public static final String ESTIMATE_APPROVED = "APPROVED";
    public static final String ESTIMATE_REJECTED = "REJECTED";

    private ServicePriceSnapshotSupport() {}

    public static String resolvePriceType(ServiceItem item) {
        if (item == null) {
            return INSPECTION_REQUIRED;
        }
        BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        BigDecimal min = item.getMinPrice();
        BigDecimal max = item.getMaxPrice();
        if (price.signum() <= 0 && (min == null || min.signum() <= 0)) {
            return INSPECTION_REQUIRED;
        }
        if (min != null && min.signum() > 0
                && (price.signum() <= 0 || price.compareTo(min) == 0)
                && (max == null || max.compareTo(min) > 0)) {
            return STARTING_FROM;
        }
        if (max != null && max.signum() > 0 && price.signum() > 0 && max.compareTo(price) > 0) {
            return STARTING_FROM;
        }
        return FIXED;
    }

    public static BigDecimal resolveDisplayPrice(ServiceItem item, String priceType) {
        if (item == null) {
            return null;
        }
        if (INSPECTION_REQUIRED.equals(priceType)) {
            return null;
        }
        if (STARTING_FROM.equals(priceType)) {
            if (item.getMinPrice() != null && item.getMinPrice().signum() > 0) {
                return item.getMinPrice();
            }
            if (item.getPrice() != null && item.getPrice().signum() > 0) {
                return item.getPrice();
            }
            return null;
        }
        return item.getPrice() != null && item.getPrice().signum() > 0 ? item.getPrice() : null;
    }

    public static String defaultEstimateStatus(String priceType) {
        if (FIXED.equals(priceType)) {
            return ESTIMATE_NOT_REQUIRED;
        }
        return ESTIMATE_PENDING;
    }

    public static String normalizePriceType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return switch (v) {
            case "FIXED" -> FIXED;
            case "STARTING_FROM", "STARTINGFROM", "FROM" -> STARTING_FROM;
            case "INSPECTION_REQUIRED", "INSPECTIONREQUIRED", "INSPECTION", "QUOTE" -> INSPECTION_REQUIRED;
            default -> null;
        };
    }
}
