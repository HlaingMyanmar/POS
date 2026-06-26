package org.sspd.servicemgmt.brandoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.brandoptions.dto.BrandDTO;
import org.sspd.servicemgmt.brandoptions.service.BrandService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandDTO>>> getAll(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Brand List", service.findAll())
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandDTO>> getBrandById(@PathVariable Long id){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Brand Found",service.findById(id))
        );

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<BrandDTO>> createBrand(
            @Valid @RequestBody BrandDTO dto){

        BrandDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Brand Created Successfully", created)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandDTO>> updateBrand(
            @PathVariable Long id,
            @Valid @RequestBody BrandDTO dto){

        BrandDTO update = service.update(id,dto);

        return ResponseEntity.ok(
                new ApiResponse<>(true,"Brand Updated Successfully",update)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BRAND_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandDTO>> deleteBrand(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Brand Deleted Successfully",null)
        );
    }
}
