package org.sspd.servicemgmt.stockoptions.stockmovementoptions.controller;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.dto.StockMovementDTO;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stock-movements")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StockMovementController {

    private final StockMovementService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockMovementDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Stock Movements fetched", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<StockMovementDTO>>> getByProduct(@PathVariable Integer productId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Stock Movements by product", service.findByProduct(productId)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @GetMapping("/product/{productId}/range")
    public ResponseEntity<ApiResponse<List<StockMovementDTO>>> getByProductAndDate(
            @PathVariable Integer productId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @NotNull LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @NotNull LocalDateTime to) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Stock Movements by product and date", service.findByProductAndDateRange(productId, from, to)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @GetMapping("/movement-type")
    public ResponseEntity<ApiResponse<List<StockMovementDTO>>> getByMovementType(@RequestParam MovementType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Stock Movements by type", service.findByMovementType(type)));
    }
}
