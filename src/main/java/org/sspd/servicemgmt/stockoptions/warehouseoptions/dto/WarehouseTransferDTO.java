package org.sspd.servicemgmt.stockoptions.warehouseoptions.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WarehouseTransferDTO {
    private Integer id;
    private String transferNo;
    private Integer productId;
    private String productName;
    private Integer fromWarehouseId;
    private String fromWarehouseName;
    private Integer toWarehouseId;
    private String toWarehouseName;
    private Integer qty;
    private LocalDateTime transferredAt;
    private String transferredBy;
    private String remark;
}
