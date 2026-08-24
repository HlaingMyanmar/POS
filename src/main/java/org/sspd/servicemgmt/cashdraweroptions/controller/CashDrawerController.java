package org.sspd.servicemgmt.cashdraweroptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.cashdraweroptions.dto.*;
import org.sspd.servicemgmt.cashdraweroptions.model.*;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cash-drawers")
@RequiredArgsConstructor
public class CashDrawerController {
    private final CashDrawerService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_CASH_DRAWER_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CashDrawerSession>>> all() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cash drawer sessions retrieved", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CASH_DRAWER_READ')")
    @GetMapping("/{id}/movements")
    public ResponseEntity<ApiResponse<List<CashDrawerMovement>>> movements(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cash movements retrieved", service.movements(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CASH_DRAWER_MANAGE')")
    @PostMapping("/open")
    public ResponseEntity<ApiResponse<CashDrawerSession>> open(@Valid @RequestBody CashDrawerRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Cash drawer opened", service.open(request.getAmount(), request.getNote())));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CASH_DRAWER_MANAGE')")
    @PostMapping("/{id}/cash-in")
    public ResponseEntity<ApiResponse<CashDrawerSession>> cashIn(@PathVariable Integer id, @Valid @RequestBody CashMovementRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cash-in recorded", service.cashIn(id, request.getAmount(), request.getReason())));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CASH_DRAWER_MANAGE')")
    @PostMapping("/{id}/cash-out")
    public ResponseEntity<ApiResponse<CashDrawerSession>> cashOut(@PathVariable Integer id, @Valid @RequestBody CashMovementRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cash-out recorded", service.cashOut(id, request.getAmount(), request.getReason())));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_CASH_DRAWER_MANAGE')")
    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CashDrawerSession>> close(@PathVariable Integer id, @Valid @RequestBody CashDrawerRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cash drawer closed", service.close(id, request.getAmount(), request.getNote())));
    }
}
