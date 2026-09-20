package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SupplierCreditApplicationDTO {
    private Integer id;
    private String applicationNo;
    private Integer supplierId;
    private Integer purchaseId;
    private String purchaseCode;
    private BigDecimal amount;
    private BigDecimal advanceUsed;
    private BigDecimal returnCreditUsed;
    private LocalDateTime appliedAt;
    private String appliedBy;
    private String reason;
    private Boolean voided;
    private LocalDateTime voidedAt;
    private String voidedBy;
    private String voidReason;
}
