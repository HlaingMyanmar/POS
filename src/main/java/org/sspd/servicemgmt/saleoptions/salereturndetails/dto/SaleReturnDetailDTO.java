package org.sspd.servicemgmt.saleoptions.salereturndetails.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SaleReturnDetailDTO {
    private Integer returnId;
    private Integer productId;
    private String productName;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private List<String> serialNumbers;
}
