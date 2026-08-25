package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Goods Receipt payload — converts PO lines into a Purchase voucher.
 */
@Data
public class PurchaseOrderReceiveDTO {
    private Integer staffId;

    // လက်ခံမည့် line များ (detailId + qty) — null/empty ဆိုလျှင် ကျန်ရှိသမျှအားလုံး
    private List<ReceiveLine> lines;

    private LocalDate dueDate;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal otherCharges;
    private String remark;
    private String supplierInvoiceNo;
    private String varianceReason;
    private Integer paymentMethodId;
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;

    @Data
    public static class ReceiveLine {
        private Integer detailId;
        private Integer qty;
        private Integer damagedQty;
        private Integer rejectedQty;
        private BigDecimal invoiceUnitCost;
        private Integer warrantyMonths;
        private List<Integer> itemWarranties;
        private List<String> serialNumbers;
        private List<String> serialConditions;
        private List<String> serialPhotos;
        private String batchNumber;
        private java.time.LocalDate expiryDate;
    }
}
