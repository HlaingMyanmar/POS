package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class SupplierPaymentDTO {
    private Integer id; private String paymentNo; private Integer supplierId; private String supplierName;
    private Integer paymentMethodId; private String paymentMethodName;
    private BigDecimal totalAmount; private BigDecimal allocatedAmount; private BigDecimal advanceAmount;
    private LocalDateTime paymentDate; private String transactionNo; private String paidBy; private String remark;
    private List<Allocation> allocations;
    @Data @Builder public static class Allocation {
        private Integer purchaseId; private String purchaseCode; private BigDecimal amount; private BigDecimal remainingDue;
    }
}
