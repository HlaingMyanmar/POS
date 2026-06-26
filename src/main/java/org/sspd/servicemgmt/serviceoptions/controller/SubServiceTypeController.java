package org.sspd.servicemgmt.serviceoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.serviceoptions.dto.SubServiceTypeDTO;
import org.sspd.servicemgmt.serviceoptions.service.SubServiceTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sub-service-types")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SubServiceTypeController {

    private final SubServiceTypeService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_READ')")
    @GetMapping("/by-type/{typeId}")
    ResponseEntity<ApiResponse<List<SubServiceTypeDTO>>> getByType(@PathVariable Integer typeId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sub service types", service.findByServiceType(typeId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_READ')")
    @GetMapping("/active/by-type/{typeId}")
    ResponseEntity<ApiResponse<List<SubServiceTypeDTO>>> getActiveByType(@PathVariable Integer typeId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Active sub service types", service.findActiveByServiceType(typeId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_CREATE')")
    @PostMapping
    ResponseEntity<ApiResponse<SubServiceTypeDTO>> create(@RequestBody SubServiceTypeDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", service.save(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_UPDATE')")
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<SubServiceTypeDTO>> update(@PathVariable Integer id, @RequestBody SubServiceTypeDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_DELETE')")
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
