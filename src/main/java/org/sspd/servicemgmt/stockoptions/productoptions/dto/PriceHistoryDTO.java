package org.sspd.servicemgmt.stockoptions.productoptions.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class PriceHistoryDTO {
    Integer purchaseId;
    String purchaseCode;
    LocalDateTime purchaseDate;
    Integer supplierId;
    String supplierName;
    Integer quantity;
    BigDecimal unitCost;
    BigDecimal weightedAverageCost;
}
