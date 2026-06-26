package org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.dto.StockAdjustmentDTO;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.service.StockAdjustmentService;

@RestController
@RequestMapping("/api/v1/stock-adjustments")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StockAdjustmentController {

    private final StockAdjustmentService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_ADJUSTMENT_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StockAdjustmentDTO>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Stock adjustments fetched", service.findAll(search, page, size)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_ADJUSTMENT_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockAdjustmentDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Stock adjustment detail", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_ADJUSTMENT_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<StockAdjustmentDTO>> create(@Valid @RequestBody StockAdjustmentDTO dto) {
        StockAdjustmentDTO created = service.save(dto);
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Stock adjustment created", created));
    }
}
