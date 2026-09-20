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
    private String source;
    private String requestedServiceName;
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
