package org.sspd.servicemgmt.servicejoboptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.customeroptions.model.Customer;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.saleoptions.model.CreditStatus;
import org.sspd.servicemgmt.shelflocationoptions.model.ShelfLocation;
import org.sspd.servicemgmt.staffoptions.model.Staff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_jobs", indexes = {
    @Index(name = "idx_sj_status",        columnList = "status"),
    @Index(name = "idx_sj_customer",      columnList = "customer_id"),
    @Index(name = "idx_sj_received_date", columnList = "received_date"),
    @Index(name = "idx_sj_payment_status",columnList = "payment_status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ServiceJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "job_no", unique = true, nullable = false, length = 20)
    private String jobNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id")
    private Staff assignedStaff;

    @Column(name = "item_name", length = 200)
    private String itemName;

    @Column(name = "serial_no", length = 120)
    private String serialNo;

    @Column(name = "color", length = 80)
    private String color;

    @Column(name = "item_condition", columnDefinition = "TEXT")
    private String itemCondition;

    @Column(name = "device_conditions", columnDefinition = "TEXT")
    private String deviceConditions; // JSON: [{"name":"Screen","status":"Good"},...]

    @Column(name = "accessories_received", columnDefinition = "TEXT")
    private String accessories; // comma-separated: "Charger,USB Cable"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shelf_location_id")
    private ShelfLocation shelfLocation;

    @Column(name = "problem_desc", columnDefinition = "TEXT")
    private String problemDesc;

    @Column(name = "diagnosis_notes", columnDefinition = "TEXT")
    private String diagnosisNotes;

    @Column(name = "estimated_cost", precision = 15, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "final_cost", precision = 15, scale = 2)
    private BigDecimal finalCost;

    @Column(name = "received_date")
    private LocalDateTime receivedDate;

    @Column(name = "estimated_completion")
    private LocalDateTime estimatedCompletion;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "delivered_date")
    private LocalDateTime deliveredDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ServiceJobStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Column(name = "booking_id")
    private Integer bookingId;

    @Column(name = "sale_id")
    private Integer saleId;

    @Column(name = "is_rework")
    private Boolean rework;

    @Column(name = "parent_job_id")
    private Integer parentJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rework_type", length = 20)
    private ReworkType reworkType;

    @Column(name = "replacement_item_name", length = 200)
    private String replacementItemName;

    @Column(name = "replacement_serial_no", length = 120)
    private String replacementSerialNo;

    @Column(name = "replacement_reason", columnDefinition = "TEXT")
    private String replacementReason;

    @Builder.Default
    @Column(name = "discount_amount", precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_foc")
    private Boolean foc = Boolean.FALSE;

    @Builder.Default
    @Column(name = "net_amount", precision = 15, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "due_amount", precision = 15, scale = 2)
    private BigDecimal dueAmount = BigDecimal.ZERO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "credit_status", length = 20)
    private CreditStatus creditStatus = CreditStatus.Not_Credit;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @OneToMany(mappedBy = "serviceJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceJobLine> lines;

    @OneToMany(mappedBy = "serviceJob", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceJobPart> productParts;

    @PrePersist
    protected void onCreate() {
        if (receivedDate == null) receivedDate = LocalDateTime.now();
        if (status == null) status = ServiceJobStatus.RECEIVED;
        if (estimatedCost == null) estimatedCost = BigDecimal.ZERO;
        if (finalCost == null) finalCost = BigDecimal.ZERO;
        if (lines == null) lines = new ArrayList<>();
        if (productParts == null) productParts = new ArrayList<>();
    }
}
