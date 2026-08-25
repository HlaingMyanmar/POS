package org.sspd.servicemgmt.creditoptions.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerCreditApplyRequest {
    private Integer customerId;
    private Integer saleId;
    private Integer staffId;
    private BigDecimal amount;
    private String reason;
}
