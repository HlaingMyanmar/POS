package org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManufacturingFormulaItemDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private String productCode;
    private Boolean hasSerial;
    private Integer qty;
    private BigDecimal unitCost;
}
