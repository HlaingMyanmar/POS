package org.sspd.servicemgmt.unitsoptions.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UnitDTO {

    private int id;
    @NotBlank(message = "Required Unit Name")
    private String unitName;
    private String description;
}
