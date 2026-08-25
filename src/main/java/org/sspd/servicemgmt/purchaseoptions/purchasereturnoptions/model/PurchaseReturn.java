package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_returns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseReturn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @Column(name = "return_no", unique = true, nullable = false)
    private String returnNo;

    @Column(name = "return_date")
    private LocalDateTime returnDate = LocalDateTime.now();

    @Column(name = "total_return_amount")
    private BigDecimal totalReturnAmount = BigDecimal.ZERO;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "status", length = 30)
    private String status = "DRAFT";

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "submitted_by", length = 120)
    private String submittedBy;
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    @Column(name = "approved_by", length = 120)
    private String approvedBy;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    @Column(name = "approval_note", length = 500)
    private String approvalNote;

    @Column(length = 120)
    private String rejectedBy;
    @Column
    private LocalDateTime rejectedAt;
    @Column(length = 500)
    private String rejectionReason;
    @Column(length = 30) private String resolutionType;
    @Column(length = 120) private String rmaNumber;
    @Column private LocalDateTime claimDate;
    @Column private LocalDateTime expectedResolutionDate;
    @Column(length = 160) private String supplierContact;
    @Column(length = 30) private String claimStatus;
    @Column private Integer replacementExpectedQty;
    @Column private Integer replacementReceivedQty;
    @Column private Integer goodsReceiptId;

    @Column(name = "carrier", length = 120)
    private String carrier;
    @Column(name = "tracking_no", length = 120)
    private String trackingNo;
    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;
    @Column(name = "supplier_received_at")
    private LocalDateTime supplierReceivedAt;
    @Column(name = "delivery_proof", columnDefinition = "LONGTEXT")
    private String deliveryProof;

    @Builder.Default
    @Column(name = "shipping_cost_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingCostAmount = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "shipping_payer_responsibility", length = 20, nullable = false)
    private String shippingPayerResponsibility = "COMPANY";
    @Builder.Default
    @Column(name = "company_shipping_portion", precision = 18, scale = 2, nullable = false)
    private BigDecimal companyShippingPortion = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "supplier_shipping_portion", precision = 18, scale = 2, nullable = false)
    private BigDecimal supplierShippingPortion = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "shipping_allocation_method", length = 20, nullable = false)
    private String shippingAllocationMethod = "VALUE";
    @Column(name = "shipping_payment_method_id")
    private Integer shippingPaymentMethodId;
    @Column(name = "shipping_transaction_reference", length = 120)
    private String shippingTransactionReference;
    @Column(name = "shipping_posted_at")
    private LocalDateTime shippingPostedAt;

    @Column(name = "settlement_type", length = 30)
    private String settlementType;
    @Column(name = "expected_credit_amount", precision = 18, scale = 2)
    private BigDecimal expectedCreditAmount;
    @Column(name = "supplier_credit_note_no", length = 120)
    private String supplierCreditNoteNo;
    @Column(name = "supplier_credit_note_amount", precision = 18, scale = 2)
    private BigDecimal supplierCreditNoteAmount;
    @Column(name = "credit_variance", precision = 18, scale = 2)
    private BigDecimal creditVariance;
    @Column(name = "credit_variance_reason", length = 500)
    private String creditVarianceReason;
    @Column(name = "settled_at")
    private LocalDateTime settledAt;
    @Column(name = "settlement_reference", length = 120)
    private String settlementReference;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReturnDetail> details = new ArrayList<>();
}
