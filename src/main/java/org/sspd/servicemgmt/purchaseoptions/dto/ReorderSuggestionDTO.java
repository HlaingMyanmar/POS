package org.sspd.servicemgmt.purchaseoptions.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReorderSuggestionDTO {
    private Integer productId;
    private String productName;
    private String productCode;
    private Boolean hasSerial;
    private Integer stockQty;
    private Integer reorderLevel;
    private Integer suggestedQty;
    private BigDecimal lastCost;
}
