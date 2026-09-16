package org.sspd.servicemgmt.journaloption.detail.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class JournalDetailDTO {
    @NotNull(message = "Account is required")
    private Integer accountId;
    private String accountName;
    @DecimalMin(value = "0.00", message = "Debit cannot be negative")
    private BigDecimal debit;
    @DecimalMin(value = "0.00", message = "Credit cannot be negative")
    private BigDecimal credit;
}
