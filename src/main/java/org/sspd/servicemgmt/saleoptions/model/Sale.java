package org.sspd.servicemgmt.saleoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.saleoptions.model.CreditStatus;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales", indexes = {
    @Index(name = "idx_sale_date",           columnList = "sale_date"),
    @Index(name = "idx_sale_customer",       columnList = "customer_id"),
    @Index(name = "idx_sale_payment_status", columnList = "payment_status"),
    @Index(name = "idx_sale_code",           columnList = "sale_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "sale_code", nullable = false, unique = true, length = 50)
    private String saleCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = true)
    private Staff staff;

    @Column(name = "sale_date")
    private LocalDateTime saleDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_status", length = 20)
    private CreditStatus creditStatus = CreditStatus.Not_Credit;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 15, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_foc")
    private Boolean foc = Boolean.FALSE;

    @Column(name = "net_amount", precision = 15, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "due_amount", precision = 15, scale = 2)
    private BigDecimal dueAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.Pending;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Builder.Default
    @Column(name = "is_voided", nullable = false)
    private Boolean voided = Boolean.FALSE;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "quotation_id")
    private Integer quotationId;

    @Column(name = "quotation_code", length = 50)
    private String quotationCode;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleDetail> details = new ArrayList<>();
}
