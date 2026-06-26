package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseReturnDTO {
    private Integer id;
    private Integer purchaseId;
    private String returnNo;
    private LocalDateTime returnDate;
    private BigDecimal totalReturnAmount;
    // Amount actually refunded by supplier; defaults to totalReturnAmount when not provided
    private BigDecimal refundAmount;
    private Integer paymentMethodId;
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;
    private String status;
    private LocalDateTime voidedAt;
    private String voidReason;
    private String reason;
    private List<PurchaseReturnDetailDTO> details;
}
