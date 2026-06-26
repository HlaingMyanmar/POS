package org.sspd.servicemgmt.rbacoptions.permissionoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.dto.PermissionDTO;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.service.PermissionService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService services;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PERMISSIONS_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Permission List", services.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PERMISSIONS_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionDTO>> getPermissionById(@PathVariable Long id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Permission Found", services.findById(id))
        );
    }

    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_PERMISSION_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<PermissionDTO>> createPermission(
            @Valid @RequestBody PermissionDTO dto) {

        PermissionDTO created = services.save(dto);

        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Permission Created Successfully", created)
        );
    }

    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_PERMISSION_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionDTO>> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody PermissionDTO dto) {

        PermissionDTO updated = services.update(id, dto);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Permission Updated Successfully", updated)
        );
    }

    @PreAuthorize("hasRole('ADMINISTRATOR') or hasAuthority('CAN_ACCESS_PERMISSION_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable Long id) {

        services.delete(id);

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Permission Deleted Successfully", null)
        );
    }



}
