package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class SettleDTO {
    private BigDecimal finalCost;
    private BigDecimal discountAmount;
    private Boolean foc;
    private BigDecimal paidAmount;
    private LocalDate dueDate;
    private Integer paymentMethodId;
    private Integer paymentAccountId;
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;
}
