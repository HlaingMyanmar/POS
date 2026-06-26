package org.sspd.servicemgmt.saleoptions.saledetails.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class SaleDetailDTO {
    private Integer productId;
    private String productName;
    private Integer qty;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private Boolean foc;
    private Integer warrantyMonths;
    private LocalDate warrantyExpiryDate;
    private List<String> serialNumbers;
}
