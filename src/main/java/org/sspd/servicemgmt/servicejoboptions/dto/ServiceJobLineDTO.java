package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceJobLineDTO {
    private Integer id;
    private Integer serviceItemId;
    private String serviceItemName;
    private Integer qty;
    private BigDecimal price;
    private BigDecimal subtotal;
    private Integer warrantyMonths;
}
