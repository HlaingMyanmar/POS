package org.sspd.servicemgmt.accountingoptions.periodlock.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.accountingoptions.periodlock.model.AccountingPeriodLock;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodLockService;
import java.util.List;

@RestController @RequestMapping("/api/v1/accounting-period-locks") @RequiredArgsConstructor
public class AccountingPeriodLockController {
    private final AccountingPeriodLockService service;
    @GetMapping public ResponseEntity<ApiResponse<List<AccountingPeriodLock>>> list(){return ResponseEntity.ok(new ApiResponse<>(true,"Period locks",service.list()));}
    @PostMapping public ResponseEntity<ApiResponse<AccountingPeriodLock>> lock(@RequestBody AccountingPeriodLock body){return ResponseEntity.status(201).body(new ApiResponse<>(true,"Period locked",service.lock(body)));}
    @PostMapping("/{id}/unlock") public ResponseEntity<ApiResponse<AccountingPeriodLock>> unlock(@PathVariable Integer id){return ResponseEntity.ok(new ApiResponse<>(true,"Period unlocked",service.unlock(id)));}
}
