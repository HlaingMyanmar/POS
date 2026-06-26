package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrialBalanceLineItem {
    private String accountCode;
    private String accountName;
    private String accountType;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
}
