package org.sspd.servicemgmt.servicebookingsettingsoptions.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class ServiceBookingArrivalWindowDTO {
    private Integer id;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxCapacity;
    private Boolean active;
    private Integer displayOrder;
}
