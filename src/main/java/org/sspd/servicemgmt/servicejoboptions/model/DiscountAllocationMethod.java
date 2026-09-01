package org.sspd.servicemgmt.servicejoboptions.model;

public enum DiscountAllocationMethod {
    PRO_RATA,
    LABOR_FIRST,
    PARTS_FIRST;

    public static DiscountAllocationMethod from(String value) {
        if (value == null || value.isBlank()) return PRO_RATA;
        try {
            return DiscountAllocationMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return PRO_RATA;
        }
    }
}
