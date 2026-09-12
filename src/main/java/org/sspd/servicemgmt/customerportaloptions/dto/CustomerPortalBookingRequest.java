package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerPortalBookingRequest {
    private String serviceName;
    private String deviceName;
    private String problem;
    private LocalDateTime appointmentDate;
    private String remark;
}
