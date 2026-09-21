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
public class BookingAvailabilityPeriodDTO {
    private LocalTime opensAt;
    private LocalTime closesAt;
}
