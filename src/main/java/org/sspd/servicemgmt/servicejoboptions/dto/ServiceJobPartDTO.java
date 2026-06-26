package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ServiceJobPartDTO {
    private Integer id;
    private Integer productId;
    private String productName;
    private String productCode;
    private String productType;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal subtotal;
    private List<String> serialNumbers;
}
