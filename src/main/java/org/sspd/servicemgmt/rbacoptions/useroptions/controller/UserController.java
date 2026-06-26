package org.sspd.servicemgmt.rbacoptions.useroptions.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.rbacoptions.useroptions.dto.UserDTO;
import org.sspd.servicemgmt.rbacoptions.useroptions.service.UserService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/user")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_USERS_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAll(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"user List",service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USERS_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>>getUserById(@PathVariable Long id){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"User Found",service.findById(id))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<UserDTO>> createUser(
            @Valid @RequestBody UserDTO dto)
    {
       UserDTO created = service.save(dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"User Created Successfully",created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserDTO dto
    ){
        UserDTO updated = service.update(id,dto);

        return ResponseEntity.ok(
                new ApiResponse<>(true,"User Updated Successfully",updated)
        );

    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> deleteRole(
            @PathVariable Long id
    ){

        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"User Deleted Successfully",null)
        );

    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_ASSIGN_ROLES')")
    @PutMapping("/{userId}/role")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable Long userId,
            @RequestBody Set<Long> roleIds) {

        service.assignRole(userId, roleIds);
        return ResponseEntity.ok(new ApiResponse<>(true, "User assigned to role", null));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_USER_REMOVE_ROLES')")
    @DeleteMapping("/{userId}/role/{roleId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        service.removeRole(userId, roleId);
        return ResponseEntity.ok(new ApiResponse<>(true, "User assigned Role Removed", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO>> getMyProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserDTO dto = service.getByUsername(userDetails.getUsername());
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile", dto));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UserDTO dto) {
        UserDTO updated = service.updateProfile(userDetails.getUsername(), dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile updated", updated));
    }
}
