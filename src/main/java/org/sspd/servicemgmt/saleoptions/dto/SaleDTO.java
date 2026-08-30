package org.sspd.servicemgmt.saleoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.saleoptions.saledetails.dto.SaleDetailDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleDTO {
    private Integer id;
    private String saleCode;
    private Integer customerId;
    private String customerName;
    private Integer staffId;
    private String staffName;
    private LocalDateTime saleDate;
    private LocalDate dueDate;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private Boolean foc;
    private BigDecimal netAmount;
    private BigDecimal paidAmount;
    private BigDecimal dueAmount;
    private String paymentStatus;
    private String creditStatus;
    private String remark;
    private Boolean voided;
    private String voidReason;
    private String voidedBy;
    private LocalDateTime voidedAt;
    private Integer quotationId;
    private String quotationCode;
    private String warehouseName;
    private Integer warehouseId;
    private Boolean managerOverride;
    private Integer managerId;
    private String overrideNote;
    private Integer paymentAccountId; // Cash=5, Bank=6 (for journal)
    private Integer paymentMethodId; // required when paidAmount > 0
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;
    private Integer arAccountId; // for credit/partial sales
    private List<SaleDetailDTO> details;

    // Internal flag: set by ServiceJobService when creating a sale solely for
    // inventory stock movement. Skips credit check and payment recording,
    // since payment is tracked at the ServiceJob level.
    private boolean serviceJobSale;
}
