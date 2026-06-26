package org.sspd.servicemgmt.unitsoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.unitsoptions.dto.UnitDTO;
import org.sspd.servicemgmt.unitsoptions.service.UnitService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/units")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UnitController {
    private final UnitService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UnitDTO>>> getAll(){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Unit List", service.findAll())
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitDTO>>getUnitById(@PathVariable Long id){
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Unit Found",service.findById(id))
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<UnitDTO>>createUnit(
            @Valid @RequestBody UnitDTO dto
    ){
        UnitDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Unit Created Successfully", created)
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UnitDTO>>updateUnit(
            @PathVariable Long id,
            @Valid @RequestBody UnitDTO dto
    ){
        UnitDTO update = service.update(id,dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true,"Unit Updated Successfully",update)
        );
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_UNIT_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>>deleteUnit(@PathVariable Long id){
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Unit Deleted Successfully",null)
        );
    }

}
