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
@AllArgsConstructor
@NoArgsConstructor
public class TrialBalanceDTO {
    private LocalDate asOf;
    private List<TrialBalanceLineItem> lines;
    private BigDecimal grandTotalDebit;
    private BigDecimal grandTotalCredit;
    private boolean balanced;
}
