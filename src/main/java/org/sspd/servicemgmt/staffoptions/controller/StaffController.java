package org.sspd.servicemgmt.staffoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.staffoptions.dto.StaffDTO;
import org.sspd.servicemgmt.staffoptions.service.StaffService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staffs")
@RequiredArgsConstructor
public class StaffController {
    private final StaffService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Staff List", service.findAll()));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<StaffDTO>>> getActiveStaffs() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Active Staff List", service.findAllActive()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StaffDTO>> create(@Valid @RequestBody StaffDTO dto) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Created", service.save(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffDTO>> update(@PathVariable Integer id, @Valid @RequestBody StaffDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Staff deactivated", null));
    }
}