package org.sspd.servicemgmt.stockoptions.opening.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.opening.dto.OpeningStockRequest;
import org.sspd.servicemgmt.stockoptions.opening.service.OpeningStockService;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.dto.StockAdjustmentDTO;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.service.StockAdjustmentService;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockInventoryController {
    private final OpeningStockService openingStockService;
    private final StockAdjustmentService stockAdjustmentService;

    @PostMapping("/opening")
    public ResponseEntity<ApiResponse<Void>> opening(@Valid @RequestBody OpeningStockRequest body) {
        openingStockService.createOpening(body);
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Opening stock recorded", null));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<ApiResponse<StockAdjustmentDTO>> adjustment(@Valid @RequestBody StockAdjustmentDTO body) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Stock adjustment created", stockAdjustmentService.save(body)));
    }
}
