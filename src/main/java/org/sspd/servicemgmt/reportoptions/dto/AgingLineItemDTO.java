package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingLineItemDTO {
    private Integer partyId;
    private String referenceNo;
    private String partyName;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal originalAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private int daysPastDue;       // 0 if not yet overdue
    private int daysToDue;         // positive if not yet due, 0 if overdue
    private String bucket;         // Current, 0-30, 31-60, 61-90, >90
}
