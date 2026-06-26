package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class RecentSaleDTO {
    private Integer id;
    private String saleCode;
    private String customerName;
    private BigDecimal amount;
    private String date;
    private String status;
}
