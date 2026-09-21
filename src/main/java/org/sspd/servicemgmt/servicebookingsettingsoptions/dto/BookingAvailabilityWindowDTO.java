package org.sspd.servicemgmt.servicebookingsettingsoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingAvailabilityWindowDTO {
    private Integer windowId;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private int capacity;
    private int booked;
    private int remaining;
    /** AVAILABLE | FEW_LEFT | FULL | UNAVAILABLE */
    private String state;
}
