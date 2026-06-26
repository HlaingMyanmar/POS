package org.sspd.servicemgmt.rbacoptions.roleoptions.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.dto.PermissionDTO;

import java.util.List;

@Data
public class RoleDTO {
    private Integer id;
    @NotBlank(message = "Required Role Name")
    private String name;
    private String description;
    private List<PermissionDTO> permissions;


}
