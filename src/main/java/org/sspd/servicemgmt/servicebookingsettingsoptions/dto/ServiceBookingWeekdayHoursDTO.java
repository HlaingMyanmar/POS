package org.sspd.servicemgmt.servicebookingsettingsoptions.dto;

import lombok.Data;

import java.time.LocalTime;

@Data
public class ServiceBookingWeekdayHoursDTO {
    private Integer id;
    private String dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean active;
    private Integer displayOrder;
}
