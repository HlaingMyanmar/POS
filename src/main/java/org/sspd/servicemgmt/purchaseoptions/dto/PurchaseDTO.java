package org.sspd.servicemgmt.purchaseoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto.PurchaseDetailDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PurchaseDTO {
    private Integer id;
    private String purchaseCode;
    private Integer supplierId;
    private String supplierName;
    private Integer staffId;
    private LocalDateTime purchaseDate;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;
    private BigDecimal returnAmount;
    private BigDecimal refundAmount;
    private BigDecimal netAmount;
    private BigDecimal supplierCreditAmount;
    private BigDecimal dueAmount;
    private String paymentStatus;
    private String remark;
    private String staffName;
    private List<PurchaseDetailDTO> details;
    private Integer paymentMethodId;
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;



}
