package org.sspd.servicemgmt.barcodesettingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.barcodesettingoptions.dto.BarcodeLabelSettingsDTO;
import org.sspd.servicemgmt.barcodesettingoptions.service.BarcodeLabelSettingsService;

@RestController
@RequestMapping("/api/v1/barcode-label-settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BarcodeLabelSettingsController {

    private final BarcodeLabelSettingsService service;

    @GetMapping
    public ResponseEntity<ApiResponse<BarcodeLabelSettingsDTO>> get() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Barcode label settings", service.getSettings()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BarcodeLabelSettingsDTO>> save(@RequestBody BarcodeLabelSettingsDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", service.saveSettings(dto)));
    }
}
