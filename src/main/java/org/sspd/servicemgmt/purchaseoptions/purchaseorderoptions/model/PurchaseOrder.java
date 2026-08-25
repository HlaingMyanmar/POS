package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ToString
@Entity
@Table(name = "purchase_orders", indexes = {
    @Index(name = "idx_po_status", columnList = "status"),
    @Index(name = "idx_po_supplier", columnList = "supplier_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Version
    private Long version;

    @Column(name = "po_code", unique = true, nullable = false)
    private String poCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "order_date")
    private LocalDateTime orderDate = LocalDateTime.now();

    // ပစ္စည်းရောက်မည့် မျှော်မှန်းရက်
    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Enumerated(EnumType.STRING)
    private POStatus status = POStatus.PENDING_APPROVAL;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderDetail> details = new ArrayList<>();
}
