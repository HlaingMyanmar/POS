package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;

@Data
public class BookingDeviceDTO {
    private Integer id;
    private String deviceType;
    private String brand;
    private String model;
    private String serialNumber;
    private String color;
    private String accessories;
    private String problemDesc;
    private String deviceConditions;
}
