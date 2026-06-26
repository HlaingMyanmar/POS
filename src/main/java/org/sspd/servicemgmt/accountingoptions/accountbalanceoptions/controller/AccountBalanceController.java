package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.dto.AccountBalanceDTO;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.service.AccountBalanceService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/account-balances")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class AccountBalanceController {

    private final AccountBalanceService service;

    // အကောင့်အားလုံး၏ လက်ကျန်ငွေများကို list ထုတ်ပြရန်
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountBalanceDTO>>> getAllBalances() {

        log.debug("Fetching all account balances");
        return ResponseEntity.ok(
                new ApiResponse<>(true, "All Account Balances", service.findAll())
        );


    }

    // Account ID တစ်ခုတည်းအတွက် လက်ကျန်ငွေကြည့်ရန်
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<AccountBalanceDTO>> getByAccountId(@PathVariable Integer accountId) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Balance for Account", service.findByAccountId(accountId))
        );
    }

    // Account ID နှင့် Year တွဲဖက်၍ ရှာရန်
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_READ')")
    @GetMapping("/filter")
    public ResponseEntity<ApiResponse<AccountBalanceDTO>> getByAccountAndYear(
            @RequestParam Integer accountId,
            @RequestParam String fiscalYear) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Filtered Balance Found", service.findByAccountAndYear(accountId, fiscalYear))
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNT_BALANCE_UPDATE')")
    @PostMapping("/set-opening")
    public ResponseEntity<ApiResponse<AccountBalanceDTO>> setOpening(
            @RequestParam Integer accountId,
            @RequestParam BigDecimal amount,
            @RequestParam Integer staffId,
            @RequestParam Integer paymentMethodId) { // ဒါလေး ထပ်တိုးပါ

        AccountBalanceDTO result = service.setOpeningBalance(accountId, amount, staffId, paymentMethodId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Opening Balance Set", result));
    }
}