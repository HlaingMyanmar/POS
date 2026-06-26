package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.saleoptions.saledetails.repository.SaleDetailRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports/sales-ranking")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SalesRankingController {

    private final SaleDetailRepository saleDetailRepo;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> topProducts(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDt = from != null && !from.isBlank() ? LocalDateTime.parse(from + "T00:00:00") : null;
        LocalDateTime toDt   = to   != null && !to.isBlank()   ? LocalDateTime.parse(to   + "T23:59:59") : null;

        List<Object[]> rows = saleDetailRepo.topProductsByQty(fromDt, toDt);
        List<Map<String, Object>> result = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId",   r[0]);
            m.put("productName", r[1]);
            m.put("productCode", r[2]);
            m.put("totalQty",    ((Number) r[3]).longValue());
            m.put("totalAmount", (BigDecimal) r[4]);
            return m;
        }).toList();

        return ResponseEntity.ok(new ApiResponse<>(true, "Top Products", result));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> monthlySummary() {
        List<Object[]> rows = saleDetailRepo.monthlySalesSummary();
        List<Map<String, Object>> result = rows.stream().map(r -> {
            int year  = ((Number) r[0]).intValue();
            int month = ((Number) r[1]).intValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year",        year);
            m.put("month",       month);
            m.put("monthLabel",  YearMonth.of(year, month).toString());
            m.put("totalQty",    ((Number) r[2]).longValue());
            m.put("totalAmount", (BigDecimal) r[3]);
            return m;
        }).toList();
        return ResponseEntity.ok(new ApiResponse<>(true, "Monthly Summary", result));
    }
}
