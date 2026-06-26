package org.sspd.servicemgmt.saleoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalePaymentDTO {
    private BigDecimal paidAmount;
    private Integer paymentMethodId;
    private Integer paymentAccountId; // Cash=5, Bank=6
    private String transactionNo;
    private Integer arAccountId; // override default AR
    private Integer staffId;
    private String note;
    private List<PaymentTransactionDTO> payments;
}
