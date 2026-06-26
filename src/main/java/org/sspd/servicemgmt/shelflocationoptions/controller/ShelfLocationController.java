package org.sspd.servicemgmt.shelflocationoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.shelflocationoptions.dto.ShelfLocationDTO;
import org.sspd.servicemgmt.shelflocationoptions.service.ShelfLocationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shelf-locations")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ShelfLocationController {

    private final ShelfLocationService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SHELF_LOCATION_READ')")
    @GetMapping
    ResponseEntity<ApiResponse<List<ShelfLocationDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Shelf locations", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SHELF_LOCATION_READ')")
    @GetMapping("/active")
    ResponseEntity<ApiResponse<List<ShelfLocationDTO>>> getActive() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Active shelf locations", service.findActive()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SHELF_LOCATION_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<ShelfLocationDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Shelf location", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SHELF_LOCATION_CREATE')")
    @PostMapping
    ResponseEntity<ApiResponse<ShelfLocationDTO>> create(@RequestBody ShelfLocationDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", service.create(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SHELF_LOCATION_UPDATE')")
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<ShelfLocationDTO>> update(@PathVariable Integer id, @RequestBody ShelfLocationDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SHELF_LOCATION_DELETE')")
    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
