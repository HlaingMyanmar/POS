package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerPromoQuoteDTO {
    private String promoCode;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal discountAmount;
    private BigDecimal discountPercent;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderAmount;
    private BigDecimal eligibleSubtotal;
    private BigDecimal itemsTotal;
}
