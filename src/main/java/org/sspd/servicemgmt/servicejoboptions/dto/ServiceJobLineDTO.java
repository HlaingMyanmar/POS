package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceJobLineDTO {
    private Integer id;
    private Integer serviceItemId;
    private String serviceItemName;
    private Integer qty;
    private BigDecimal catalogPrice;
    private BigDecimal estimatedPrice;
    private BigDecimal approvedPrice;
    private BigDecimal billedPrice;
    private BigDecimal price;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String priceChangeReason;
    private Boolean priceOverrideApproved;
    private String priceOverrideApprovedBy;
    private Integer warrantyMonths;
    private Boolean warrantyCovered;
    private String confirmationStatus;
}
