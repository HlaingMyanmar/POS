package org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseDetailDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private Integer qty;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
    private BigDecimal allocatedLandedCost;
    private String batchNumber;
    private LocalDate expiryDate;
    private Integer warrantyMonths;
    private List<Integer> itemWarranties;
    private List<String> serialNumbers;
    private List<String> serialConditions;
    private List<String> serialPhotos;
}
