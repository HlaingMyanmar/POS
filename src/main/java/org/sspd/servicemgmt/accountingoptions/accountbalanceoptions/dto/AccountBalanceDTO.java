package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceDTO {
    private Integer id;
    private Integer accountId;
    private String accountName;
    private String fiscalYear;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
    private LocalDateTime lastUpdated;
}