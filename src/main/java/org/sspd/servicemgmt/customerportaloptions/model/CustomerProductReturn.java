package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_product_returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProductReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "return_no", nullable = false, unique = true, length = 24)
    private String returnNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @Column(name = "sale_id")
    private Integer saleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 20)
    private String status;

    @Builder.Default
    @Column(name = "delivery_status", nullable = false, length = 30)
    private String deliveryStatus = "PICKUP_REQUESTED";

    @Column(name = "delivery_note", length = 500)
    private String deliveryNote;

    @Column(name = "delivery_updated_at")
    private LocalDateTime deliveryUpdatedAt;

    @Column(name = "delivery_updated_by", length = 255)
    private String deliveryUpdatedBy;

    @Builder.Default
    @Column(name = "inventory_applied", nullable = false)
    private boolean inventoryApplied = false;

    @Builder.Default
    @Column(name = "accounting_posted", nullable = false)
    private boolean accountingPosted = false;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "customer_note", length = 1000)
    private String customerNote;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "inspect_note", length = 1000)
    private String inspectNote;

    @Column(name = "refund_amount", precision = 15, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_payment_method_id")
    private Integer refundPaymentMethodId;

    @Column(name = "refund_reference", length = 120)
    private String refundReference;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_recorded_by", length = 255)
    private String refundRecordedBy;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "received_by", length = 255)
    private String receivedBy;

    @Column(name = "inspected_by", length = 255)
    private String inspectedBy;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "productReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerProductReturnLine> lines = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "productReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomerProductReturnPhoto> photos = new ArrayList<>();
}
