package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerOrderRatingDTO {
    private Integer id;
    private Integer orderId;
    private String orderNo;
    private String orderType;
    private Integer saleId;
    private Integer customerId;
    private String customerName;
    private Integer rating;
    private Integer productRating;
    private Integer serviceRating;
    private String comment;
    /** Same as comment; kept for older shop UI. */
    private String review;
    private boolean hidden;
    private String hiddenBy;
    private LocalDateTime hiddenAt;
    private String hideReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime editableUntil;
    private boolean editable;
    private List<Line> products = new ArrayList<>();

    @Data
    public static class Line {
        private Integer productId;
        private String productName;
        private Integer rating;
    }
}
