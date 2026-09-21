package org.sspd.servicemgmt.servicebookingsettingsoptions.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Data
public class ServiceBookingDateExceptionDTO {
    private Integer id;
    private LocalDate exceptionDate;
    private Boolean closed;
    private LocalTime opensAt;
    private LocalTime closesAt;
    private String reason;
    private Set<Integer> disabledArrivalWindowIds = new HashSet<>();
}
