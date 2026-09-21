package org.sspd.servicemgmt.servicebookingsettingsoptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceBookingSettingsDTO {
    private String outdoorTransportationNotice;
    private BigDecimal outdoorTransportationFee;
    /** Max device photos per booking item (default 50). */
    private Integer maxPhotosPerItem;
    /** Customer-app copy when shop cancels/rejects a booking. */
    private String bookingRejectionMessage;
    /** When false, customer cannot request ONSITE outdoor service. */
    private Boolean outdoorBookingEnabled;
    /** Reason shown while outdoor booking is disabled. */
    private String outdoorBookingDisabledReason;

    private Integer maxAdvanceBookingDays;
    private Integer minNoticeHours;
    private Boolean allowSameDayBooking;
    private Boolean allowEmergencyRequest;
}
