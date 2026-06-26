package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseReturnDetailDTO {
    private Integer returnId;
    private Integer productId;
    private String productName;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private java.util.List<String> serialNumbers;
}
