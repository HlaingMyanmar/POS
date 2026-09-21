package org.sspd.servicemgmt.bookingoptions.dto;

import lombok.Data;

@Data
public class BookingItemComponentDTO {
    private Integer id;
    private String componentType;
    private String brand;
    private String model;
    private String specification;
    private String serialNo;
    private Integer quantity;
    private String conditionNote;
}
