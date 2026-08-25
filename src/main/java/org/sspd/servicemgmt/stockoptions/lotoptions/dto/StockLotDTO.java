package org.sspd.servicemgmt.stockoptions.lotoptions.dto;
import lombok.*;import java.time.*;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockLotDTO{private Integer id;private Integer productId;private String productCode;private String productName;private Integer purchaseId;private String purchaseCode;private String batchNumber;private LocalDate expiryDate;private String warehouseName;private Integer receivedQty;private Integer remainingQty;private Long daysToExpiry;private String alertLevel;}
