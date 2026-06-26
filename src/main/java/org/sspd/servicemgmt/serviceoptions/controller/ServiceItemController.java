package org.sspd.servicemgmt.serviceoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.serviceoptions.dto.ServiceItemDTO;
import org.sspd.servicemgmt.serviceoptions.service.ServiceItemService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_READ')")
    @GetMapping
    ResponseEntity<ApiResponse<List<ServiceItemDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Services", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_READ')")
    @GetMapping("/active")
    ResponseEntity<ApiResponse<List<ServiceItemDTO>>> getActive() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Active services", service.findActive()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_READ')")
    @GetMapping("/by-type/{typeId}")
    ResponseEntity<ApiResponse<List<ServiceItemDTO>>> getByType(@PathVariable Integer typeId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Services by type", service.findByType(typeId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_CREATE')")
    @PostMapping
    ResponseEntity<ApiResponse<ServiceItemDTO>> create(@RequestBody ServiceItemDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", service.save(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_UPDATE')")
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<ServiceItemDTO>> update(@PathVariable Integer id, @RequestBody ServiceItemDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_DELETE')")
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
