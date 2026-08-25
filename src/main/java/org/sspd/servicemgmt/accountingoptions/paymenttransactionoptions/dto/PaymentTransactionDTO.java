package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentTransactionDTO {
    private Integer id;
    private Integer referenceId;
    private String referenceType;
    private Integer paymentMethodId;
    private String paymentMethodName;
    private BigDecimal amount;
    private String transactionNo;
    private java.time.LocalDateTime paymentDate;

    private String referenceCode; // PUR-001 သို့မဟုတ် INV-001
    private String entityName;
    private Boolean reversed;
    private java.time.LocalDateTime reversedAt;
    private String reversedBy;
    private String reversalReason;
}
