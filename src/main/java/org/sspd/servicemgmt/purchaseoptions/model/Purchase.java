package org.sspd.servicemgmt.purchaseoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@ToString
@Entity
@Table(name = "purchases", indexes = {
    @Index(name = "idx_purchase_date",           columnList = "purchase_date"),
    @Index(name = "idx_purchase_supplier",       columnList = "supplier_id"),
    @Index(name = "idx_purchase_payment_status", columnList = "paymentStatus")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "purchase_code", unique = true, nullable = false)
    private String purchaseCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "purchase_date")
    private LocalDateTime purchaseDate = LocalDateTime.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "payment_term_days")
    private Integer paymentTermDays;

    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "discount_amount")
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "paid_amount")
    private BigDecimal paidAmount = BigDecimal.ZERO;
    @Column(name = "return_amount")
    private BigDecimal returnAmount = BigDecimal.ZERO;
    @Column(name = "refund_amount")
    private BigDecimal refundAmount = BigDecimal.ZERO;
    @Column(name = "net_amount")
    private BigDecimal netAmount = BigDecimal.ZERO;
    @Column(name = "supplier_credit_amount")
    private BigDecimal supplierCreditAmount = BigDecimal.ZERO;
    private BigDecimal dueAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.Pending;

    // DRAFT / CONFIRMED / CANCELLED — legacy rows (NULL) are treated as CONFIRMED
    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_status")
    private PurchaseStatus status;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount = BigDecimal.ZERO;

    // Landing cost — freight/customs/other charges added to purchase cost
    @Column(name = "other_charges")
    private BigDecimal otherCharges = BigDecimal.ZERO;

    // Supplier paper invoice attachment (base64)
    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_data", columnDefinition = "LONGTEXT")
    private String attachmentData;

    // Linked Purchase Order (nullable)
    @Column(name = "po_id")
    private Integer poId;

    @Column(columnDefinition = "TEXT")
    private String remark;

    public boolean isDraft() {
        return status == PurchaseStatus.DRAFT;
    }

    public boolean isCancelled() {
        return status == PurchaseStatus.CANCELLED;
    }

    public boolean isEffectivelyConfirmed() {
        return status == null || status == PurchaseStatus.CONFIRMED;
    }

    // အဝယ်အသေးစိတ်စာရင်းများနှင့် ချိတ်ဆက်ခြင်း
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseDetail> details = new ArrayList<>();
}

