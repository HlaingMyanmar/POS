package org.sspd.servicemgmt.staffoptions.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StaffDTO {
    private Integer id;

    @NotBlank(message = "Staff name is required")
    private String name;

    private String phone;

    @NotBlank(message = "Role is required")
    private String role;

    private boolean isActive;

    private BigDecimal basicSalary = BigDecimal.ZERO;
}