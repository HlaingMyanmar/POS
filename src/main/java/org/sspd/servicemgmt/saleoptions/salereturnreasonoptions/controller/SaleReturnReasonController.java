package org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.dto.SaleReturnReasonDTO;
import org.sspd.servicemgmt.saleoptions.salereturnreasonoptions.service.SaleReturnReasonService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sale-return-reasons")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SaleReturnReasonController {
    private final SaleReturnReasonService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SaleReturnReasonDTO>>> all(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Sale return reasons", service.findAll(activeOnly)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SaleReturnReasonDTO>> create(@RequestBody SaleReturnReasonDTO dto) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Reason created", service.save(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SaleReturnReasonDTO>> update(
            @PathVariable Integer id, @RequestBody SaleReturnReasonDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Reason updated", service.update(id, dto)));
    }
}
