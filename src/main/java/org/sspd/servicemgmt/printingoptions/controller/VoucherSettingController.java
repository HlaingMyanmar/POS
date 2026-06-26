package org.sspd.servicemgmt.printingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.printingoptions.dto.PrintRequest.DocumentType;
import org.sspd.servicemgmt.printingoptions.dto.VoucherSettingDto;
import org.sspd.servicemgmt.printingoptions.service.VoucherSettingService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/voucher-settings")
@RequiredArgsConstructor
public class VoucherSettingController {

    private final VoucherSettingService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VoucherSettingDto>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", service.getAll()));
    }

    @GetMapping("/{type}")
    public ResponseEntity<ApiResponse<VoucherSettingDto>> getByType(@PathVariable String type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "OK",
                service.getByDocumentType(DocumentType.valueOf(type.toUpperCase()))));
    }

    @PutMapping("/{type}")
    public ResponseEntity<ApiResponse<VoucherSettingDto>> save(
            @PathVariable String type,
            @RequestBody VoucherSettingDto dto,
            @AuthenticationPrincipal UserDetails principal) {
        String actor = principal != null ? principal.getUsername() : "system";
        VoucherSettingDto withType = new VoucherSettingDto(
                dto.id(), type.toUpperCase(), dto.paperSize(),
                dto.marginTopMm(), dto.marginBottomMm(), dto.marginLeftMm(), dto.marginRightMm(),
                dto.headerHeightPx(), dto.contHeaderHeightPx(), dto.infoBlocksHeightPx(), dto.tableHeaderHeightPx(),
                dto.rowHeightPx(), dto.totalsAreaHeightPx(), dto.footerHeightPx(), dto.safetyMarginPx(),
                dto.showLogo(), dto.showQrCode(), dto.showSignatures(), dto.showPaymentHistory(), dto.showSerial(),
                dto.sign1Label(), dto.sign2Label(), dto.voucherTitle(), dto.footerNote(), dto.customerNotice(),
                dto.headerFontFamily(),      dto.headerFontSizePx(),
                dto.infoFontFamily(),        dto.infoFontSizePx(),
                dto.tableHeaderFontFamily(), dto.tableHeaderFontSizePx(),
                dto.tableDataFontFamily(),   dto.tableDataFontSizePx(),
                dto.footerFontFamily(),      dto.footerFontSizePx(),
                dto.noticeFontFamily(),      dto.noticeFontSizePx(),
                null, null, null, null
        );
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", service.save(withType, actor)));
    }

    @PostMapping("/{type}/reset")
    public ResponseEntity<ApiResponse<VoucherSettingDto>> reset(
            @PathVariable String type,
            @AuthenticationPrincipal UserDetails principal) {
        String actor = principal != null ? principal.getUsername() : "system";
        return ResponseEntity.ok(new ApiResponse<>(true, "Reset",
                service.resetToDefaults(DocumentType.valueOf(type.toUpperCase()), actor)));
    }
}
