package org.sspd.servicemgmt.creditoptions.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerCreditTermDTO {
    private Integer id;

    @NotNull(message = "Customer is required")
    private Integer customerId;

    @Min(value = 0, message = "Credit limit cannot be negative")
    private BigDecimal creditLimit;

    @Min(value = 0, message = "Credit days cannot be negative")
    private Integer creditDays;

    private Boolean creditAllowed;

    private String customerName;
}
