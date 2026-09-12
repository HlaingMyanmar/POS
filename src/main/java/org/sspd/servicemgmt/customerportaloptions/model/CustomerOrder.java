package org.sspd.servicemgmt.customerportaloptions.model;

import jakarta.persistence.*;
import lombok.*;
import org.sspd.servicemgmt.customeroptions.model.Customer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "customer_orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_orders_customer_idempotency",
                columnNames = {"customer_id", "idempotency_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Builder.Default
    @Column(name = "loyalty_awarded", nullable = false)
    private boolean loyaltyAwarded = false;

    @Column(name = "order_no", nullable = false, unique = true, length = 20)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerOrderStatus status;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** DELIVERY or PICKUP */
    @Column(name = "order_type", length = 20)
    private String orderType;

    /** PROFILE or OTHER — only when orderType = DELIVERY */
    @Column(name = "delivery_location_mode", length = 20)
    private String deliveryLocationMode;

    /** Snapshot of delivery address at order time */
    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;

    /** Recipient phone when delivering to another location (or profile phone snapshot) */
    @Column(name = "delivery_phone", length = 40)
    private String deliveryPhone;

    @Column(name = "township_id")
    private Integer townshipId;

    @Column(name = "township_name", length = 255)
    private String townshipName;

    @Column(name = "ward_id")
    private Integer wardId;

    @Column(name = "ward_name", length = 120)
    private String wardName;

    @Column(name = "delivery_charge", precision = 15, scale = 2)
    private java.math.BigDecimal deliveryCharge;
    @Column(name="shipping_state", nullable=false) @Builder.Default
    private String shippingState = "LEGACY";
    @Column(name="shipping_version", nullable=false) @Builder.Default
    private Integer shippingVersion = 0;
    @Builder.Default
    @Column(name="shipping_renegotiated", nullable=false)
    private boolean shippingRenegotiated = false;
    @Column(name="shipping_weight_kg", precision=12, scale=3)
    private java.math.BigDecimal shippingWeightKg;
    @Column(name="shipping_reason", length=1000)
    private String shippingReason;
    @Column(name="shipping_snapshot", columnDefinition="TEXT")
    private String shippingSnapshot;

    /** OWN = shop delivers (charge applies). HANDOFF = third party; customer is not billed delivery. */
    @Column(name = "delivery_handler", length = 20)
    private String deliveryHandler;

    @Column(name = "quoted_delivery_charge", precision = 15, scale = 2)
    private java.math.BigDecimal quotedDeliveryCharge;

    @Column(name = "items_total", precision = 15, scale = 2)
    private java.math.BigDecimal itemsTotal;

    @Column(name = "promo_id")
    private Integer promoId;

    @Column(name = "promo_code", length = 40)
    private String promoCode;

    @Column(name = "discount_type", length = 20)
    private String discountType;

    @Column(name = "discount_value", precision = 15, scale = 2)
    private java.math.BigDecimal discountValue;

    @Column(name = "discount_amount", precision = 15, scale = 2)
    private java.math.BigDecimal discountAmount;

    @Column(name = "discount_max", precision = 15, scale = 2)
    private java.math.BigDecimal discountMax;

    @Column(name = "eligible_subtotal", precision = 15, scale = 2)
    private java.math.BigDecimal eligibleSubtotal;

    @Column(name = "promo_snapshot", columnDefinition = "TEXT")
    private String promoSnapshot;

    /**
     * Delivery progress for DELIVERY orders:
     * PENDING → PACKING → PACKED → HANDED_TO_RIDER → OUT_FOR_DELIVERY → IN_TRANSIT → DELIVERED
     */
    @Column(name = "delivery_status", length = 30)
    private String deliveryStatus;

    @Column(name = "delivery_current_location", length = 255)
    private String deliveryCurrentLocation;

    @Column(name = "delivery_scheduled_at")
    private LocalDateTime deliveryScheduledAt;

    /** Customer's requested delivery datetime, pending shop confirmation. */
    @Column(name = "requested_delivery_at")
    private LocalDateTime requestedDeliveryAt;

    @Column(name = "delivery_person_phone", length = 40)
    private String deliveryPersonPhone;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** NONE | CONFIRMED | NOT_RECEIVED — customer says whether goods are in their hands. */
    @Builder.Default
    @Column(name = "customer_receipt_state", nullable = false, length = 20)
    private String customerReceiptState = "NONE";

    @Column(name = "customer_received_at")
    private LocalDateTime customerReceivedAt;

    @Column(name = "customer_receipt_note", length = 500)
    private String customerReceiptNote;

    /** Device / delivery GPS captured when the customer placed this order (not only profile GPS). */
    @Column(name = "order_latitude", precision = 10, scale = 7)
    private java.math.BigDecimal orderLatitude;

    @Column(name = "order_longitude", precision = 10, scale = 7)
    private java.math.BigDecimal orderLongitude;

    @Column(name = "order_location_accuracy")
    private Double orderLocationAccuracy;

    @Column(name = "order_location_at")
    private LocalDateTime orderLocationAt;

    @Column(name = "order_location_source", length = 20)
    private String orderLocationSource;

    @Builder.Default
    @Column(name = "payment_state", nullable = false, length = 30)
    private String paymentState = "NONE";

    @Builder.Default
    @Column(name = "payment_choice", nullable = false, length = 30)
    private String paymentChoice = "TRANSFER";

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** Snapshot of company pickup deposit % at place time. */
    @Column(name = "deposit_percent", precision = 5, scale = 2)
    private java.math.BigDecimal depositPercent;

    @Column(name = "deposit_amount", precision = 15, scale = 2)
    private java.math.BigDecimal depositAmount;

    @Builder.Default
    @Column(name = "reservation_active", nullable = false)
    private boolean reservationActive = false;

    private LocalDateTime reservationExpiresAt;

    private Integer paymentMethodId;

    /** Shop channel used for remaining amount after deposit (may differ from transfer channel). */
    @Column(name = "collection_payment_method_id")
    private Integer collectionPaymentMethodId;

    @Column(name = "collection_amount", precision = 15, scale = 2)
    private java.math.BigDecimal collectionAmount;

    @Column(name = "collection_proof_id")
    private Integer collectionProofId;

    @Column(name = "collection_reference", length = 120)
    private String collectionReference;

    @Column(name = "collection_at")
    private LocalDateTime collectionAt;

    @Column(name = "collection_recorded_by", length = 255)
    private String collectionRecordedBy;

    @Column(name = "settlement_action", length = 20)
    private String settlementAction;
    @Column(name = "settlement_amount", precision = 15, scale = 2)
    private java.math.BigDecimal settlementAmount;
    @Column(name = "settlement_kept_amount", precision = 15, scale = 2)
    private java.math.BigDecimal settlementKeptAmount;
    @Column(name = "settlement_payment_method_id")
    private Integer settlementPaymentMethodId;
    @Column(name = "settlement_reference", length = 120)
    private String settlementReference;
    @Column(name = "settlement_at")
    private LocalDateTime settlementAt;
    @Column(name = "settlement_recorded_by", length = 255)
    private String settlementRecordedBy;

    @Column(length = 2000)
    private String paymentInstructions;

    @Column(length = 1000)
    private String paymentReviewNote;

    private String paymentVerifiedBy;

    private LocalDateTime paymentVerifiedAt;

    private Integer latestProofId;

    private Integer completedSaleId;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CustomerOrderLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = CustomerOrderStatus.PENDING;
        if (paymentState == null) paymentState = "NONE";
        if (paymentChoice == null) paymentChoice = "TRANSFER";
        if (customerReceiptState == null) customerReceiptState = "NONE";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
