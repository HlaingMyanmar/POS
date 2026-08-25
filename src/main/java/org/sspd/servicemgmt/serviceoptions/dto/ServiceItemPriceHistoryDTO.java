package org.sspd.servicemgmt.serviceoptions.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class ServiceItemPriceHistoryDTO {
    private Integer id;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal oldCost;
    private BigDecimal newCost;
    private String changedBy;
    private LocalDateTime changedAt;
}
