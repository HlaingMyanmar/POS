package org.sspd.servicemgmt.rbacoptions.permissionoptions.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PermissionDTO {

    private Long id;

    @NotBlank(message = "Required Permission")
    private String name;
    private String description;
}
