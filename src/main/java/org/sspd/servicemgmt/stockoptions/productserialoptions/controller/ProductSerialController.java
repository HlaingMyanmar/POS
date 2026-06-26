package org.sspd.servicemgmt.stockoptions.productserialoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.productserialoptions.dto.ProductSerialDTO;
import org.sspd.servicemgmt.stockoptions.productserialoptions.service.ProductSerialService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-serials")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProductSerialController {

    private final ProductSerialService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductSerialDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Serial List", service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @GetMapping("/by-product/{productId}")
    public ResponseEntity<ApiResponse<List<ProductSerialDTO>>> getByProductId(@PathVariable Integer productId) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Serials by Product", service.findByProductId(productId))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @GetMapping("/by-serial/{serialNumber}")
    public ResponseEntity<ApiResponse<ProductSerialDTO>> getBySerialNumber(@PathVariable String serialNumber) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Serial Found", service.findBySerialNumber(serialNumber))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductSerialDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Serial Found", service.findById(id))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductSerialDTO>> create(
            @Valid @RequestBody ProductSerialDTO dto) {
        ProductSerialDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Product Serial Created Successfully", created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductSerialDTO>> update(
            @PathVariable Integer id,
            @Valid @RequestBody ProductSerialDTO dto) {
        ProductSerialDTO updated = service.update(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Serial Updated Successfully", updated)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteById(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Serial Deleted Successfully", null)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_UPDATE')")
    @PutMapping("/{id}/photo")
    public ResponseEntity<ApiResponse<Void>> updatePhoto(
            @PathVariable Integer id,
            @RequestBody java.util.Map<String, String> body) {
        service.updatePhoto(id, body.get("photoBase64"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Photo updated", null));
    }

    // 🔹 Delete by Serial Number
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/by-serials")
    public ResponseEntity<ApiResponse<List<ProductSerialDTO>>> getBySerialNumbers(@RequestBody List<String> serials) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Serials Found", service.findBySerialNumbers(serials))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_DELETE')")
    @DeleteMapping("/by-serial/{serialNumber}")
    public ResponseEntity<ApiResponse<Void>> deleteBySerialNumber(@PathVariable String serialNumber) {
        service.deleteBySerialNumber(serialNumber);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Serial Deleted Successfully by Serial Number", null)
        );
    }
}
