package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.bookingoptions.dto.BookingRequestPhotoDTO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPortalBookingRequest {
    private String serviceName;
    private String requestType;
    private String deviceCategory;
    private String deviceName;
    private String problem;
    private String serviceMode;
    private String serviceAddress;
    private String urgency;
    private String contactPreference;
    private LocalDateTime appointmentDate;
    private String remark;
    private List<BookingRequestPhotoDTO> photos = new ArrayList<>();
}
