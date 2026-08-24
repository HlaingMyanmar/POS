package org.sspd.servicemgmt.stockoptions.stockmovementoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.dto.ProductStockHistoryDTO;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.ProductStockHistoryService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProductStockHistoryController {

    private final ProductStockHistoryService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @GetMapping("/stock-history")
    public ResponseEntity<ApiResponse<ProductStockHistoryDTO>> getAllHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(new ApiResponse<>(true, "All product stock history fetched", service.getHistory(null, from, to, type, search, page, size)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @GetMapping("/{productId}/stock-history")
    public ResponseEntity<ApiResponse<ProductStockHistoryDTO>> getHistory(
            @PathVariable Integer productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Product stock history fetched", service.getHistory(productId, from, to, type, search, page, size)));
    }
}
