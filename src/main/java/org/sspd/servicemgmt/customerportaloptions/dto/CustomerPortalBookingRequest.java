package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.bookingoptions.dto.BookingRequestPhotoDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPortalBookingRequest {
    private Integer serviceId;
    private String serviceName;
    /** Price shown to the customer in the app (snapshot source). */
    private java.math.BigDecimal displayedPrice;
    /** FIXED | STARTING_FROM | INSPECTION_REQUIRED — optional; server derives when missing. */
    private String priceType;
    private String requestType;
    private String deviceCategory;
    private String deviceName;
    private String problem;
    private String serviceMode;
    private String serviceAddress;
    private String urgency;
    private String contactPreference;
    private LocalDateTime appointmentDate;
    /** ONSITE: authoritative service day. */
    private LocalDate serviceDate;
    /** ONSITE: authoritative arrival window. */
    private Integer arrivalWindowId;
    private LocalTime preferredTime;
    private Boolean preferredAnytime;
    private String customerPreferenceNote;
    private String remark;
    private List<BookingRequestPhotoDTO> photos = new ArrayList<>();
}
