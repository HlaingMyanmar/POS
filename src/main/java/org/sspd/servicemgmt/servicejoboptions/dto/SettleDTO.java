package org.sspd.servicemgmt.servicejoboptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class SettleDTO {
    private BigDecimal finalCost;
    private BigDecimal discountAmount;
    /** PRO_RATA, LABOR_FIRST, or PARTS_FIRST */
    private String discountAllocationMethod;
    private Boolean foc;
    private BigDecimal paidAmount;
    private LocalDate dueDate;
    private Integer paymentMethodId;
    private Integer paymentAccountId;
    private Integer warehouseId;
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;
    private BigDecimal paymentDiscountAmount;
    private String paymentDiscountApprovalNote;
}
