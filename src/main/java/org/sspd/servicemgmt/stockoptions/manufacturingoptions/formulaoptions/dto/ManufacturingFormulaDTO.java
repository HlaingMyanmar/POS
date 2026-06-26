package org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ManufacturingFormulaDTO {
    private Integer id;
    private String name;
    private String description;
    private String finishedProductName;
    private Integer finishedProductBrandId;
    private String finishedProductBrandName;
    private Integer finishedProductCategoryId;
    private String finishedProductCategoryName;
    private Integer finishedProductUnitId;
    private String finishedProductUnitName;
    private String finishedProductType;
    private BigDecimal finishedProductSellingPrice;
    @Builder.Default
    private List<ManufacturingFormulaItemDTO> items = new ArrayList<>();
}
