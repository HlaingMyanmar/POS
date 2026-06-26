package org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PurchaseDetailDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private Integer qty;
    private BigDecimal unitCost;
    private BigDecimal subtotal;
    private Integer warrantyMonths;
    private List<Integer> itemWarranties;
    private List<String> serialNumbers;
    private List<String> serialConditions;
    private List<String> serialPhotos;
}
