package org.sspd.servicemgmt.supplieroptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.supplieroptions.dto.SupplierDTO;
import org.sspd.servicemgmt.supplieroptions.service.SupplierService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;


    @GetMapping
    public ResponseEntity<ApiResponse<Page<SupplierDTO>>> getAllPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Supplier Paginated List", service.findAllPaginated(pageable))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_READ')")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<SupplierDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "All Supplier List", service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_READ')")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SupplierDTO>>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Search Results", service.searchSuppliers(keyword))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Supplier Found", service.findById(id))
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<SupplierDTO>> create(@Valid @RequestBody SupplierDTO dto) {
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Supplier Created Successfully", service.save(dto))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierDTO>> update(
            @PathVariable Integer id,
            @Valid @RequestBody SupplierDTO dto) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Supplier Updated Successfully", service.update(id, dto))
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_SUPPLIER_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Supplier Deleted Successfully", null)
        );
    }
}