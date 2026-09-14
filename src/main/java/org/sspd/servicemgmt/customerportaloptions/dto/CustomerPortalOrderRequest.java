package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerPortalOrderRequest {
    private String paymentChoice;
    /** Client checkout attempt id — unique per customer so retries do not duplicate orders. */
    private String idempotencyKey;

    private String note;
    private List<Line> lines = new ArrayList<>();

    /** DELIVERY or PICKUP (defaults to PICKUP if blank for older clients). */
    private String orderType;
    /** PROFILE or OTHER — required when orderType = DELIVERY. */
    private String deliveryLocationMode;
    /** Required when deliveryLocationMode = OTHER. */
    private String deliveryAddress;
    /** Recipient phone — required when deliveryLocationMode = OTHER. */
    private String deliveryPhone;
    /** Required when orderType = DELIVERY — township (kept for older clients). */
    private Integer townshipId;
    /** Optional ward override; township charge is used when omitted. */
    private Integer wardId;

    /** Customer-requested delivery local datetime (required for DELIVERY). */
    private java.time.LocalDateTime requestedDeliveryAt;

    /** Order-time / delivery GPS (optional for PICKUP; preferred for DELIVERY OTHER). */
    private java.math.BigDecimal latitude;
    private java.math.BigDecimal longitude;
    private Double locationAccuracy;
    private String locationSource;
    private String promoCode;

    @Data
    public static class Line {
        private Integer productId;
        private Integer qty;
    }
}
