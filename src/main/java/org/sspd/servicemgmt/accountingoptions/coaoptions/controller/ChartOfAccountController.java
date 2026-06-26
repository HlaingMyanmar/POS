package org.sspd.servicemgmt.accountingoptions.coaoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.accountingoptions.coaoptions.dto.ChartOfAccountDTO;
import org.sspd.servicemgmt.accountingoptions.coaoptions.service.ChartOfAccountService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chart-of-accounts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChartOfAccountController {

    private final ChartOfAccountService service;

    // ၁။ အကောင့်များအားလုံးကို Flat List (တန်းစီပြီး) ထုတ်ပေးခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChartOfAccountDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Chart of Account List", service.findAll())
        );
    }

    // ၂။ အကောင့်များကို Tree Structure (အဆင့်ဆင့်) ထုတ်ပေးခြင်း (UI အတွက် အရေးကြီးသည်)
    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_READ')")
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<ChartOfAccountDTO>>> getTree() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Chart of Account Tree List", service.findTree())
        );
    }

    // ၃။ ID ဖြင့် တစ်ခုတည်းကို ရှာဖွေခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ChartOfAccountDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Chart of Account Found", service.findById(id))
        );
    }

    // ၄။ အကောင့်အသစ်ဆောက်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ChartOfAccountDTO>> create(
            @Valid @RequestBody ChartOfAccountDTO dto) {
        ChartOfAccountDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Chart of Account Created Successfully", created)
        );
    }

    // ၅။ အကောင့်အချက်အလက် ပြင်ဆင်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_UPDATE')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ChartOfAccountDTO>> update(
            @PathVariable Integer id,
            @Valid @RequestBody ChartOfAccountDTO dto) {
        ChartOfAccountDTO updated = service.update(id, dto);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Chart of Account Updated Successfully", updated)
        );
    }

    // ၆။ အကောင့်ကို ဖျက်ခြင်း (လက်အောက်ခံအကောင့်ရှိလျှင် Service က Error ပြပါလိမ့်မည်)
    @PreAuthorize("hasAuthority('CAN_ACCESS_COA_DELETE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Chart of Account Deleted Successfully", null)
        );
    }
}