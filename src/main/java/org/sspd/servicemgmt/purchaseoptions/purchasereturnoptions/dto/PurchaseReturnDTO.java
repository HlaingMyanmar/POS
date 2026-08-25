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
    private Long version;
    private Integer purchaseId;
    private String purchaseCode;
    private String supplierName;
    private String returnNo;
    private LocalDateTime returnDate;
    private BigDecimal totalReturnAmount;
    // Amount actually refunded by supplier; defaults to totalReturnAmount when not provided
    private BigDecimal refundAmount;
    private Integer paymentMethodId;
    private String paymentMethodName;
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;
    private String status;
    private LocalDateTime voidedAt;
    private String voidReason;
    private String reason;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String approvalNote;
    private String rejectedBy;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private String resolutionType;
    private String rmaNumber;
    private LocalDateTime claimDate;
    private LocalDateTime expectedResolutionDate;
    private String supplierContact;
    private String claimStatus;
    private Integer replacementExpectedQty;
    private Integer replacementReceivedQty;
    private Integer goodsReceiptId;
    private List<PurchaseReturnActivityDTO> activities;
    private List<PurchaseReturnAttachmentDTO> attachments;
    private String carrier;
    private String trackingNo;
    private LocalDateTime dispatchedAt;
    private LocalDateTime supplierReceivedAt;
    private String deliveryProof;
    private BigDecimal shippingCostAmount;
    private String shippingPayerResponsibility;
    private BigDecimal companyShippingPortion;
    private BigDecimal supplierShippingPortion;
    private String shippingAllocationMethod;
    private Integer shippingPaymentMethodId;
    private String shippingPaymentMethodName;
    private String shippingTransactionReference;
    private LocalDateTime shippingPostedAt;
    private PaymentTransactionDTO shippingPaymentTransaction;
    private String settlementType;
    private BigDecimal expectedCreditAmount;
    private String supplierCreditNoteNo;
    private BigDecimal supplierCreditNoteAmount;
    private BigDecimal creditVariance;
    private String creditVarianceReason;
    private LocalDateTime settledAt;
    private String settlementReference;
    private List<PurchaseReturnDetailDTO> details;
}
