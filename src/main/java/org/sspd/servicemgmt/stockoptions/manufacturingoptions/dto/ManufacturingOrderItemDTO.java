package org.sspd.servicemgmt.stockoptions.manufacturingoptions.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManufacturingOrderItemDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private String productCode;
    private Boolean hasSerial;
    private Integer qty;
    private BigDecimal unitCost;
    private List<Integer> selectedSerialIds;
    private List<String> selectedSerialNumbers;
    private Integer availableQty;
}
