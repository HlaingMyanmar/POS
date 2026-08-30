package org.sspd.servicemgmt.stockoptions.lotoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockLotDTO {
    private Integer id;
    private Integer productId;
    private String productCode;
    private String productName;
    private Integer purchaseId;
    private String purchaseCode;
    private String batchNumber;
    private LocalDate expiryDate;
    private String warehouseName;
    private Integer warehouseId;
    private String warehouseCode;
    private String sourceType;
    private String status;
    private Integer receivedQty;
    private Integer remainingQty;
    private Integer soldQty;
    private Long daysToExpiry;
    private String alertLevel;
}
