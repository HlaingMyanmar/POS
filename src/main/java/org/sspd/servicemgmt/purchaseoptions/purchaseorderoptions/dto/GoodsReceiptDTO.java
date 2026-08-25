package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class GoodsReceiptDTO {
    private Integer id;
    private String grnCode;
    private Integer purchaseOrderId;
    private String poCode;
    private Integer purchaseId;
    private String supplierInvoiceNo;
    private LocalDateTime receivedAt;
    private String receivedBy;
    private String matchStatus;
    private String varianceReason;
    private List<Line> lines;

    @Data @Builder
    public static class Line {
        private Integer productId;
        private String productName;
        private Integer orderedQty;
        private Integer acceptedQty;
        private Integer damagedQty;
        private Integer rejectedQty;
        private BigDecimal poUnitCost;
        private BigDecimal invoiceUnitCost;
        private BigDecimal priceVariance;
    }
}
