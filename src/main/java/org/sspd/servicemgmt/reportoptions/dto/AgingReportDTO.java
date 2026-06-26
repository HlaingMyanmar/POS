package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingReportDTO {
    private LocalDate asOf;
    private List<AgingLineItemDTO> lines;
    private BigDecimal bucketCurrent;     // not yet due
    private BigDecimal bucket0To30;
    private BigDecimal bucket31To60;
    private BigDecimal bucket61To90;
    private BigDecimal bucketOver90;
    private BigDecimal totalOutstanding;
    private int totalInvoices;
    private int totalParties;
}
