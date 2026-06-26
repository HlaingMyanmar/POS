package org.sspd.servicemgmt.accountingoptions.expenseoptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseDTO {
    private Integer id;
    private String expenseCode;
    private LocalDateTime expenseDate;
    private Integer accountId;
    private String accountName;
    private Integer paymentMethodId;
    private String paymentMethodName;
    private BigDecimal amount;
    private String description;
    private Integer staffId;
    private String staffName;
}
