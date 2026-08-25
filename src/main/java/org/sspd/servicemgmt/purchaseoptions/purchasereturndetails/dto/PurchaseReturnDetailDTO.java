package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseReturnDetailDTO {
    private Integer id;
    private Integer returnId;
    private Integer productId;
    private String productName;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private BigDecimal allocatedShippingCost;
    private java.util.List<String> serialNumbers;
    private Integer reasonId;
    private String reasonCode;
    private String reasonName;
    private Integer quarantinedQty;
    private Integer dispatchedQty;
}
