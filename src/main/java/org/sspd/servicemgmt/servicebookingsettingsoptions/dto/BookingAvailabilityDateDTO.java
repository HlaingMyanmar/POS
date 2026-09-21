package org.sspd.servicemgmt.servicebookingsettingsoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingAvailabilityDateDTO {
    private LocalDate date;
    private boolean available;
    private String reason;
    /** Open business-hour spans for the date (SHOP drop-off / display). Empty when closed. */
    @Builder.Default
    private List<BookingAvailabilityPeriodDTO> openPeriods = new ArrayList<>();
}
