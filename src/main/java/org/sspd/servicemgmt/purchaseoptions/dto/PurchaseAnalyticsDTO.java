package org.sspd.servicemgmt.purchaseoptions.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PurchaseAnalyticsDTO {
    private long voucherCount;
    private BigDecimal totalSpent;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private BigDecimal taxAmount;
    private BigDecimal withholdingTaxAmount;
    private BigDecimal landedCostAmount;
    private BigDecimal returnAmount;
    private BigDecimal foreignAmount;
    private long fxVoucherCount;
    private long grnCount;
    private long grnVarianceCount;
    @Builder.Default private List<NamedAmount> byCategory = new ArrayList<>();
    @Builder.Default private List<NamedAmount> bySupplier = new ArrayList<>();
    @Builder.Default private List<NamedAmount> byCurrency = new ArrayList<>();

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class NamedAmount {
        private String name;
        private long count;
        private BigDecimal amount;
    }
}
