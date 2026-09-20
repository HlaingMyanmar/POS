package org.sspd.servicemgmt.stockoptions.productserialoptions.service;

import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;

final class SerialMasterPolicy {

    private SerialMasterPolicy() {
    }

    static boolean isInUse(SerialStatus status) {
        return status != null && status != SerialStatus.Available;
    }

    static void assertCreateStatus(SerialStatus requested) {
        if (requested != null && requested != SerialStatus.Available) {
            throw new IllegalArgumentException("New serials must start as Available");
        }
    }

    static void assertStatusUnchanged(SerialStatus current, SerialStatus requested) {
        if (requested != null && current != null && requested != current) {
            throw new IllegalStateException(
                    "Serial status can only change through sale, service, return, or stock-adjustment workflows");
        }
    }

    static void assertCanChangeIdentity(SerialStatus status, boolean hasHistory) {
        if (isInUse(status) || hasHistory) {
            throw new IllegalStateException(
                    "Serial number and product cannot be changed after the serial has operational history");
        }
    }

    static void assertCanChangeWarranty(SerialStatus status, boolean hasHistory) {
        if (isInUse(status) || hasHistory) {
            throw new IllegalStateException(
                    "Warranty on a used serial can only change through sale or return workflows");
        }
    }

    static void assertCanHardDelete(SerialStatus status, boolean hasHistory) {
        if (isInUse(status)) {
            throw new IllegalStateException("Cannot delete a sold, used, or returned serial");
        }
        if (hasHistory) {
            throw new IllegalStateException(
                    "Cannot hard-delete a serial that appears in sale, service, return, or adjustment history");
        }
    }
}
