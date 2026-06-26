package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;

@Data
public class BookingDeviceInfoDTO {
    private Integer id;
    private String name;
    private String description;
    private String status;
    private String notice;
}
