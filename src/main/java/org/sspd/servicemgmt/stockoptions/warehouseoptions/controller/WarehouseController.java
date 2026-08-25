package org.sspd.servicemgmt.stockoptions.warehouseoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.dto.WarehouseDTO;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.dto.WarehouseTransferDTO;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.service.WarehouseService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {
    private final WarehouseService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WarehouseDTO>>> list(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Warehouses", service.list(activeOnly)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseDTO>> save(@RequestBody WarehouseDTO body) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Warehouse saved", service.save(body)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseDTO>> update(@PathVariable Integer id, @RequestBody WarehouseDTO body) {
        body.setId(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Warehouse updated", service.save(body)));
    }

    @PostMapping("/transfers")
    public ResponseEntity<ApiResponse<WarehouseTransferDTO>> transfer(@RequestBody WarehouseTransferDTO body) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Warehouse transfer recorded", service.transfer(body)));
    }

    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<List<WarehouseTransferDTO>>> transfers() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Warehouse transfers", service.transferHistory()));
    }
}
