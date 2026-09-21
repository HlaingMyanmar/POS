package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.bookingoptions.model.BookingStatus;
import org.sspd.servicemgmt.servicejoboptions.dto.ServiceJobDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BookingDTO {
    private Integer id;
    private String bookingNo;
    private Integer customerId;
    private String customerName;
    private String customerPhone;
    private LocalDate bookingDate;
    private LocalDateTime appointmentDate;
    private String complaintNote;
    private BookingStatus status;
    private String remark;
    private String rejectionReason;
    private LocalDateTime rejectedAt;
    private String rejectedBy;
    private String source;
    private String requestedServiceName;
    /** Catalog service id selected by customer (nullable). */
    private Integer requestedServiceId;
    /** Alias / explicit name snapshot (same as requestedServiceName for portal bookings). */
    private String serviceNameSnapshot;
    private java.math.BigDecimal servicePriceSnapshot;
    /** FIXED | STARTING_FROM | INSPECTION_REQUIRED */
    private String servicePriceType;
    /** NOT_REQUIRED | PENDING | APPROVED | REJECTED */
    private String estimateApprovalStatus;
    private String requestType;
    private String deviceCategory;
    private String deviceName;
    private String requestedServiceMode;
    private String serviceAddress;
    private String urgency;
    private String contactPreference;
    private LocalDate serviceDate;
    private Integer arrivalWindowId;
    private LocalTime preferredTime;
    private Boolean preferredAnytime;
    private String customerPreferenceNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<BookingItemDTO> items = new ArrayList<>();
    private List<BookingRequestPhotoDTO> requestPhotos = new ArrayList<>();
    private List<ServiceJobDTO> linkedJobs = new ArrayList<>();
    private long unconvertedItemCount;
    private boolean fullyConverted;
}
