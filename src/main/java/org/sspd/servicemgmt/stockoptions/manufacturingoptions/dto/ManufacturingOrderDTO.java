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
public class ManufacturingOrderDTO {
    private Integer id;
    private String orderCode;
    private String status;

    private String finishedProductName;
    private Integer finishedProductBrandId;
    private String finishedProductBrandName;
    private Integer finishedProductCategoryId;
    private String finishedProductCategoryName;
    private Integer finishedProductUnitId;
    private String finishedProductUnitName;
    private String finishedProductType;
    private BigDecimal finishedProductSellingPrice;
    private Integer finishedProductId;

    private String notes;
    private String createdAt;
    private String completedAt;

    @Builder.Default
    private List<ManufacturingOrderItemDTO> items = new ArrayList<>();
    private BigDecimal totalComponentCost;
}
