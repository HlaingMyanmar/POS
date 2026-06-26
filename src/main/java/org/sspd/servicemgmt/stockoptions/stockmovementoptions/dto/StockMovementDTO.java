package org.sspd.servicemgmt.stockoptions.stockmovementoptions.dto;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockMovementDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private String movementType; // IN, OUT, RETURN, ADJUST
    private Integer qty;
    private Integer referenceId;
    private String referenceType; // Purchase, Sale, etc.
    private LocalDateTime createdAt;
}
