package org.sspd.servicemgmt.supplieroptions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDTO {

    private Integer id;
    private String code;
    @NotBlank(message = "Supplier name is required")
    @Size(max = 150, message = "Name must be less than 150 characters")
    private String name;
    @Size(max = 20, message = "Phone number too long")
    private String phone;
    private String address;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
}