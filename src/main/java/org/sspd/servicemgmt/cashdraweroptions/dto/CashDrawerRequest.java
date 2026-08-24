package org.sspd.servicemgmt.cashdraweroptions.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CashDrawerRequest {
    @NotNull @DecimalMin("0.00")
    private BigDecimal amount;
    private String note;
}
