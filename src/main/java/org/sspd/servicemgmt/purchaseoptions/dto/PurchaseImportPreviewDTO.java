package org.sspd.servicemgmt.purchaseoptions.dto;
import lombok.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseImportPreviewDTO{
 private Integer totalRows;private Integer validRows;private Integer invalidRows;private List<Row> rows;private List<String> errors;
 @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder public static class Row{private Integer rowNumber;private String productCode;private Integer productId;private String productName;private Integer qty;private BigDecimal unitCost;private BigDecimal subtotal;private String batchNumber;private LocalDate expiryDate;private Boolean serialRequired;private Boolean valid;private List<String> errors;}
}
