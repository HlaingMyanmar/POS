package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model;

public enum AssignmentStatus {
    PENDING, ACTIVE, PAUSED, COMPLETED, HANDED_OVER, REJECTED, CANCELED;

    public boolean isCurrent() {
        return this == PENDING || this == ACTIVE || this == PAUSED || this == COMPLETED;
    }
}
