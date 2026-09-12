package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerProductReturnDTO {
    private Integer id;
    private String returnNo;
    private Integer orderId;
    private String orderNo;
    private Integer saleId;
    private Integer customerId;
    private String customerName;
    private String status;
    private String deliveryStatus;
    private String deliveryNote;
    private LocalDateTime deliveryUpdatedAt;
    private boolean inventoryApplied;
    private boolean accountingPosted;
    private String reason;
    private String customerNote;
    private String reviewNote;
    private String inspectNote;
    private BigDecimal refundAmount;
    private Integer refundPaymentMethodId;
    private String refundPaymentMethodName;
    private String refundReference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime completedAt;
    private List<Line> lines = new ArrayList<>();
    private List<Photo> photos = new ArrayList<>();

    @Data
    public static class Line {
        private Integer id;
        private Integer productId;
        private String productName;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
        private String serialNumber;
        private String disposition;
        private Integer replacementProductId;
        private String replacementSerialNumber;
    }

    @Data
    public static class Photo {
        private Integer id;
        private String imageType;
        private LocalDateTime submittedAt;
        private String image;
    }
}
