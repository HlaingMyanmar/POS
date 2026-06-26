package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ProfitLossLineItem {
    private String accountCode;
    private String accountName;
    private BigDecimal amount;
}
