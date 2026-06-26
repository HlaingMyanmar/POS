package org.sspd.servicemgmt.brandoptions.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrandDTO {

    private  int id;
    @NotBlank(message = "Brand Name Required")
    private String name;
    private Boolean isActive;

}
