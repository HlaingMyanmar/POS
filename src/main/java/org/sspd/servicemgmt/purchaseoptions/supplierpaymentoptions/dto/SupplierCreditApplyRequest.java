package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SupplierCreditApplyRequest {
    private Integer supplierId;
    private Integer purchaseId;
    private Integer staffId;
    private BigDecimal amount;
    private String reason;
}
