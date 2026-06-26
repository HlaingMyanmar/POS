package org.sspd.servicemgmt.stockoptions.productoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ProductDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductDTO>>> getAll(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Product List", service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<ProductDTO>>> getLowStockProducts() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Low Stock Product List", service.findLowStock())
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDTO>> getProductById(
            @PathVariable Integer id
    ){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Product Found",service.findById(id))
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ProductDTO>>createProduct(
            @Valid @RequestBody ProductDTO dto
    ){
        ProductDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Product Created Successfully", created)
        );

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDTO>>updateProduct(
            @PathVariable Integer id,
            @Valid @RequestBody ProductDTO dto
    ){
        ProductDTO update =service.update(id,dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Product Updated Successfully",update)
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>>deleteProduct(@PathVariable Integer id){
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Product Deleted Successfully",null)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @GetMapping("/{id}/next-serial-seq")
    public ResponseEntity<ApiResponse<Integer>> getNextSerialSeq(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Next serial sequence", service.getNextSerialSeq(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @PutMapping("/{id}/photo")
    public ResponseEntity<ApiResponse<Void>> updatePhoto(
            @PathVariable Integer id,
            @RequestBody java.util.Map<String, String> body) {
        service.updatePhoto(id, body.get("photoBase64"));
        return ResponseEntity.ok(new ApiResponse<>(true, "Photo updated", null));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @PostMapping("/{id}/assign-serials")
    public ResponseEntity<ApiResponse<ProductDTO>> assignSerials(
            @PathVariable Integer id,
            @RequestBody AssignSerialsRequest req) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Serials assigned",
                service.assignSerials(id, req.getSerialNumbers(), req.getWarrantyMonths())));
    }

    static class AssignSerialsRequest {
        private List<String> serialNumbers;
        private Integer warrantyMonths;
        public List<String> getSerialNumbers() { return serialNumbers; }
        public void setSerialNumbers(List<String> serialNumbers) { this.serialNumbers = serialNumbers; }
        public Integer getWarrantyMonths() { return warrantyMonths; }
        public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    }

}
