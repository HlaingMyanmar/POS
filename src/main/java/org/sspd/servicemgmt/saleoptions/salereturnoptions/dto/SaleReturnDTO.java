package org.sspd.servicemgmt.saleoptions.salereturnoptions.dto;

import lombok.Data;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.saleoptions.salereturndetails.dto.SaleReturnDetailDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleReturnDTO {
    private Integer id;
    private Integer saleId;
    private String saleCode;
    private Integer customerId;
    private String customerName;
    private Integer staffId;
    private String returnCode;
    private LocalDateTime returnDate;
    private BigDecimal totalReturnAmount;
    private BigDecimal refundAmount;
    private Integer paymentMethodId;
    private String transactionNo;
    private List<PaymentTransactionDTO> payments;
    private String reason;
    private List<SaleReturnDetailDTO> details;
}
