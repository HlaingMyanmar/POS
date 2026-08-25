package org.sspd.servicemgmt.saleoptions.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleTimelineEventDTO {
    private String type;
    private LocalDateTime at;
    private String title;
    private String detail;
    private String refCode;
    private BigDecimal amount;
}
