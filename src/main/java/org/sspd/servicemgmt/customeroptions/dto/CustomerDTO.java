package org.sspd.servicemgmt.customeroptions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerDTO {
    private Integer id;

    @NotBlank(message = "Customer name is required")
    private String name;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20)
    private String phone;

    @NotBlank(message = "Address is required")
    private String address;

    private Boolean creditHold;
    private String creditHoldReason;
    private Boolean blacklisted;
    private String blacklistReason;
}
