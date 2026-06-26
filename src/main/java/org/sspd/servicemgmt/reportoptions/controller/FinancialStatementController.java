package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.reportoptions.dto.AgingReportDTO;
import org.sspd.servicemgmt.reportoptions.dto.BalanceSheetDTO;
import org.sspd.servicemgmt.reportoptions.dto.TrialBalanceDTO;
import org.sspd.servicemgmt.reportoptions.service.AgingReportService;
import org.sspd.servicemgmt.reportoptions.service.BalanceSheetService;
import org.sspd.servicemgmt.reportoptions.service.TrialBalanceService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FinancialStatementController {

    private final TrialBalanceService trialBalanceService;
    private final BalanceSheetService balanceSheetService;
    private final AgingReportService agingReportService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @GetMapping("/trial-balance")
    public ResponseEntity<ApiResponse<TrialBalanceDTO>> getTrialBalance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Trial Balance", trialBalanceService.getTrialBalance(asOf)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @GetMapping("/balance-sheet")
    public ResponseEntity<ApiResponse<BalanceSheetDTO>> getBalanceSheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Balance Sheet", balanceSheetService.getBalanceSheet(asOf)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @GetMapping("/ar-aging")
    public ResponseEntity<ApiResponse<AgingReportDTO>> getArAging(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AR Aging", agingReportService.getCustomerAging(asOf)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @GetMapping("/ap-aging")
    public ResponseEntity<ApiResponse<AgingReportDTO>> getApAging(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ResponseEntity.ok(new ApiResponse<>(true, "AP Aging", agingReportService.getSupplierAging(asOf)));
    }
}
