package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountTransferDTO {
    private Integer fromPaymentMethodId;
    private Integer toPaymentMethodId;
    private BigDecimal amount;
    private Integer staffId;
    private String transactionNo;
    private String description;
}
