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
public class BalanceSheetDTO {
    private LocalDate asOf;

    private List<BalanceSheetLineItem> assets;
    private BigDecimal totalAssets;

    private List<BalanceSheetLineItem> liabilities;
    private BigDecimal totalLiabilities;

    private List<BalanceSheetLineItem> equityItems;
    private BigDecimal currentYearPnL;
    private BigDecimal totalEquity;

    private BigDecimal totalLiabilitiesAndEquity;
    private boolean balanced;
}
