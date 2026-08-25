package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SupplierPaymentRequest {
    private Integer supplierId;
    private Integer paymentMethodId;
    private Integer staffId;
    private BigDecimal amount;
    private String transactionNo;
    private String remark;
    private List<Allocation> allocations;
    @Data public static class Allocation { private Integer purchaseId; private BigDecimal amount; }
}
