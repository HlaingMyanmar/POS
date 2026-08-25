package org.sspd.servicemgmt.purchaseoptions.budget.dto;
import lombok.*; import java.math.BigDecimal; import java.time.LocalDate;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseBudgetDTO {
 private Integer id; private String name; private LocalDate dateFrom; private LocalDate dateTo;
 private Integer categoryId; private String categoryName;
 private Integer supplierId; private String supplierName;
 private BigDecimal limitAmount; private String enforcement;
 private Boolean active; private BigDecimal spentAmount; private BigDecimal remainingAmount; private BigDecimal usagePercent;
}
