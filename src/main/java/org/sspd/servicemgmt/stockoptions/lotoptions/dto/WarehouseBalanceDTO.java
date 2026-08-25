package org.sspd.servicemgmt.stockoptions.lotoptions.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WarehouseBalanceDTO {
    private String warehouseName;
    private Integer productId;
    private String productCode;
    private String productName;
    private long remainingQty;
    private long receivedQty;
    private long lotCount;
}
