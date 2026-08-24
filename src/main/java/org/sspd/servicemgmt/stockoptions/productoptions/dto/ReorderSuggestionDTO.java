package org.sspd.servicemgmt.stockoptions.productoptions.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ReorderSuggestionDTO {
    Integer productId;
    String productCode;
    String productName;
    Integer currentStock;
    Integer reorderLevel;
    Integer suggestedQuantity;
    String supplierName;
    Integer supplierId;
    BigDecimal currentCost;
}
