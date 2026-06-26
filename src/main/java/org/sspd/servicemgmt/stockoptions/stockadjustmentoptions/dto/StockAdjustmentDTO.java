package org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model.AdjustmentType;

import java.time.LocalDateTime;

@Data
public class StockAdjustmentDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private AdjustmentType adjustmentType;
    private Integer qtyChange;
    private Integer qtyBefore;
    private Integer qtyAfter;
    private String reason;
    private String serialNumbers;
    private Integer staffId;
    private String staffName;
    private LocalDateTime createdAt;
}