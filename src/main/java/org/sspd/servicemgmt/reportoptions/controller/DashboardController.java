package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.reportoptions.dto.DashboardStatsDTO;
import org.sspd.servicemgmt.reportoptions.service.DashboardService;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/dashboard")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsDTO>> getStats(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate today = LocalDate.now();
        LocalDateTime fromDate = "month".equalsIgnoreCase(period) ? today.withDayOfMonth(1).atStartOfDay() : today.atStartOfDay();
        LocalDateTime toDate = today.plusDays(1).atStartOfDay();
        if ("custom".equalsIgnoreCase(period) && from != null && to != null) {
            fromDate = LocalDate.parse(from).atStartOfDay();
            toDate = LocalDate.parse(to).plusDays(1).atStartOfDay();
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Dashboard stats", dashboardService.getStats(fromDate, toDate)));
    }
}
