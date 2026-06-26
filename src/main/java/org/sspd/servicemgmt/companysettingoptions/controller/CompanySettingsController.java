package org.sspd.servicemgmt.companysettingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.companysettingoptions.dto.CompanySettingsDTO;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;

@RestController
@RequestMapping("/api/v1/company-settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CompanySettingsController {

    private final CompanySettingsService service;

    @GetMapping
    public ResponseEntity<ApiResponse<CompanySettingsDTO>> get() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Company settings", service.getSettings()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CompanySettingsDTO>> save(@RequestBody CompanySettingsDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", service.saveSettings(dto)));
    }
}
