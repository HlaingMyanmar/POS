package org.sspd.servicemgmt.serviceoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.serviceoptions.dto.ServiceTypeDTO;
import org.sspd.servicemgmt.serviceoptions.service.ServiceTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/service-types")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ServiceTypeController {

    private final ServiceTypeService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_READ')")
    @GetMapping
    ResponseEntity<ApiResponse<List<ServiceTypeDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Service types", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_READ')")
    @GetMapping("/active")
    ResponseEntity<ApiResponse<List<ServiceTypeDTO>>> getActive() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Active service types", service.findActive()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_CREATE')")
    @PostMapping
    ResponseEntity<ApiResponse<ServiceTypeDTO>> create(@RequestBody ServiceTypeDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", service.save(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_UPDATE')")
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<ServiceTypeDTO>> update(@PathVariable Integer id, @RequestBody ServiceTypeDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_DELETE')")
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
