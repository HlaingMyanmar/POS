package org.sspd.servicemgmt.creditoptions.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CustomerPaymentDTO {
    private Integer id;

    @NotNull(message = "Customer is required")
    private Integer customerId;

    private Integer saleId; // nullable for advance payment

    private LocalDateTime paymentDate;

    @NotNull(message = "Payment amount is required")
    @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private Integer paymentMethodId;

    private String transactionNo;
    private String note;

    private Integer staffId;

    private String customerName;
    private String saleCode;
    private String paymentMethodName;
    private String staffName;
}
