package org.sspd.servicemgmt.servicejoboptions.model;

import java.util.Locale;

public enum ServiceLineConfirmationStatus {
    RECOMMENDED,
    INSPECTING,
    CUSTOMER_APPROVED,
    CUSTOMER_REJECTED,
    IN_PROGRESS,
    COMPLETED;

    public static ServiceLineConfirmationStatus from(String value) {
        if (value == null || value.isBlank()) return RECOMMENDED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RECOMMENDED;
        }
    }

    public boolean isBillable() {
        return this != CUSTOMER_REJECTED;
    }

    public boolean isCustomerConfirmed() {
        return this == CUSTOMER_APPROVED || this == IN_PROGRESS || this == COMPLETED;
    }
}
