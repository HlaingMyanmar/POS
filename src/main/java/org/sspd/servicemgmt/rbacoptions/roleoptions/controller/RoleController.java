package org.sspd.servicemgmt.rbacoptions.roleoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.rbacoptions.roleoptions.dto.RoleDTO;
import org.sspd.servicemgmt.rbacoptions.roleoptions.service.RoleService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLES_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleDTO>>>getAll(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Role List",service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLES_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDTO>>getRoleById(@PathVariable Long id){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Role Found",service.findById(id))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLE_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleDTO>> createRole(
            @Valid @RequestBody RoleDTO dto)
    {
        RoleDTO created = service.save(dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Role Created Successfully",created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLE_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDTO>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleDTO dto
    ){
        RoleDTO updated = service.update(id,dto);

        return ResponseEntity.ok(
                new ApiResponse<>(true,"Role Updated Successfully",updated)
        );

    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ROLE_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleDTO>> deleteRole(
            @PathVariable Long id
    ){

        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Role Deleted Successfully",null)
        );

    }

    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_ROLE_ASSIGN_PERMISSIONS')")
    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<Void>> assignPermissions(
            @PathVariable Long roleId,
            @RequestBody Set<Long> permissionIds) { // Permission ID list ကို လက်ခံမယ်

        service.assignPermission(roleId, permissionIds);
        return ResponseEntity.ok(new ApiResponse<>(true, "Permissions assigned to role", null));
    }

    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_ROLE_REMOVE_PERMISSIONS')")
    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable Long roleId,
            @PathVariable Long permissionId) {
        service.removePermission(roleId, permissionId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Permission Removed", null));
    }

}
