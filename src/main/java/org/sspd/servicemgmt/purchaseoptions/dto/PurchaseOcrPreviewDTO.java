package org.sspd.servicemgmt.purchaseoptions.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOcrPreviewDTO {
    private String supplierInvoiceNo;
    private String supplierHint;
    private BigDecimal suggestedTotal;
    private BigDecimal suggestedTax;
    private String rawText;
    private String note;
    @Builder.Default
    private List<Line> lines = new ArrayList<>();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Line {
        private String productHint;
        private Integer qty;
        private BigDecimal unitCost;
    }
}
