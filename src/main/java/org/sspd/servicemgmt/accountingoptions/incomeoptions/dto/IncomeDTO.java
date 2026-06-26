package org.sspd.servicemgmt.accountingoptions.incomeoptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class IncomeDTO {
    private Integer id;
    private String incomeCode;
    private LocalDateTime incomeDate;
    private Integer accountId;
    private String accountName;
    private Integer paymentMethodId;
    private String paymentMethodName;
    private BigDecimal amount;
    private String description;
    private Integer staffId;
    private String staffName;
}
