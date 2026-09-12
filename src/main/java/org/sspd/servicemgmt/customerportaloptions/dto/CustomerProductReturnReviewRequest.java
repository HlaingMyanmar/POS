package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CustomerProductReturnReviewRequest {
    private String action;
    private String note;
    private String outcome;
    private BigDecimal refundAmount;
    private Integer paymentMethodId;
    private String transactionNo;
    private String deliveryStatus;
    private List<Line> lines = new ArrayList<>();

    @Data
    public static class Line {
        private Integer id;
        private String disposition;
        private Integer replacementProductId;
        private String replacementSerialNumber;
    }
}
