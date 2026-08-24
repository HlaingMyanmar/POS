package org.sspd.servicemgmt.cashdraweroptions.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CashMovementRequest {
    @NotNull @DecimalMin(value = "0.01")
    private BigDecimal amount;
    @NotBlank
    private String reason;
}
