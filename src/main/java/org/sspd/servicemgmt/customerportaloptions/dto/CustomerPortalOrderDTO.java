package org.sspd.servicemgmt.customerportaloptions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPortalOrderDTO {
    private String paymentState;
    private String paymentChoice;
    private boolean reservationActive;
    private LocalDateTime reservationExpiresAt;
    private Long reservationExpiresAtEpochMillis;
    private Integer paymentMethodId;
    /** Display name of Payment Channel (e.g. KBZPay). */
    private String paymentMethodName;
    private Integer collectionPaymentMethodId;
    private String collectionPaymentMethodName;
    private BigDecimal collectionAmount;
    private Integer collectionProofId;
    private String collectionReference;
    private LocalDateTime collectionAt;
    private String payeeName;
    private String payeeAccountNo;
    private String paymentInstructions;
    private String paymentReviewNote;
    private String paymentVerifiedBy;
    private LocalDateTime paymentVerifiedAt;
    private Integer latestProofId;
    private Integer completedSaleId;
    /** Linked POS sale invoice after fulfill (null until payment verified + sale created). */
    private CustomerPortalPurchaseDTO completedSale;
    private Integer id;
    private String orderNo;
    private Integer customerId;
    private String customerName;
    private String customerPhone;
    private CustomerOrderStatus status;
    private String note;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private List<Line> lines = new ArrayList<>();

    /** DELIVERY or PICKUP */
    private String orderType;
    /** PROFILE or OTHER */
    private String deliveryLocationMode;
    private String deliveryAddress;
    private String deliveryPhone;

    private Integer townshipId;
    private String townshipName;
    private Integer wardId;
    private String wardName;
    private BigDecimal deliveryCharge;
    private String shippingState;
    private Integer shippingVersion;
    @JsonProperty("shippingRenegotiated")
    private boolean shippingRenegotiated;
    private BigDecimal shippingWeightKg;
    private String shippingReason;
    private String shippingSnapshot;
    /** OWN or HANDOFF — set by shop before customer transfers. */
    private String deliveryHandler;
    private BigDecimal quotedDeliveryCharge;
    private BigDecimal itemsTotal;
    private String promoCode;
    private Integer promoId;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal eligibleSubtotal;
    /** Pickup: percent of items total required as deposit (snapshotted). */
    private BigDecimal depositPercent;
    private BigDecimal depositAmount;
    /** Pickup: items total minus deposit (pay at shop). */
    private BigDecimal remainingAmount;
    private String settlementAction;
    private BigDecimal settlementAmount;
    private BigDecimal settlementKeptAmount;
    private Integer settlementPaymentMethodId;
    private String settlementPaymentMethodName;
    private String settlementReference;
    private LocalDateTime settlementAt;
    private String settlementRecordedBy;

    /** PENDING | PACKING | PACKED | HANDED_TO_RIDER | OUT_FOR_DELIVERY | IN_TRANSIT | DELIVERED */
    private String deliveryStatus;
    private String deliveryCurrentLocation;
    private LocalDateTime deliveryScheduledAt;
    private LocalDateTime requestedDeliveryAt;
    private String deliveryPersonPhone;
    private LocalDateTime deliveredAt;
    /** NONE | CONFIRMED | NOT_RECEIVED */
    private String customerReceiptState;
    private LocalDateTime customerReceivedAt;
    private String customerReceiptNote;
    private boolean awaitingCustomerReceipt;
    private boolean canRate;
    private boolean canEditRating;
    private CustomerOrderRatingDTO rating;

    /** GPS captured at order time from the customer device / delivery target. */
    private BigDecimal orderLatitude;
    private BigDecimal orderLongitude;
    private Double orderLocationAccuracy;
    private LocalDateTime orderLocationAt;
    private String orderLocationSource;

    /** Customer's saved profile GPS (for comparison on web). */
    private BigDecimal profileLatitude;
    private BigDecimal profileLongitude;

    /** Shipping / proposal history (populated on single-order fetch). */
    private List<TimelineEvent> timeline = new ArrayList<>();
    /** Delivery status history with timestamp, actor, note. */
    private List<DeliveryMilestone> deliveryMilestones = new ArrayList<>();

    @Data
    public static class DeliveryMilestone {
        private Integer id;
        private String fromStatus;
        private String toStatus;
        private String actor;
        private String actorType;
        private String note;
        private LocalDateTime at;
    }

    @Data
    public static class TimelineEvent {
        private String action;
        private String actor;
        private String details;
        private LocalDateTime at;
        private Integer version;
    }

    @Data
    public static class Line {
        private Integer id;
        private Integer productId;
        private String productName;
        private String productCode;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        /** true = serial-tracked product (qty usually = serial count at sale time). */
        private Boolean hasSerial;
    }
}
