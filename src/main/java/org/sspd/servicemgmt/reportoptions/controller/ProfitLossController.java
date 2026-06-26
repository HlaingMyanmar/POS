package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.reportoptions.dto.ProfitLossDTO;
import org.sspd.servicemgmt.reportoptions.service.ProfitLossService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ProfitLossController {

    private final ProfitLossService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @GetMapping("/profit-loss")
    public ResponseEntity<ApiResponse<ProfitLossDTO>> getProfitLoss(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Profit & Loss report", service.getProfitLoss(from, to)));
    }
}
