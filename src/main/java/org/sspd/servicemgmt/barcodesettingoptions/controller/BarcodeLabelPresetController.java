package org.sspd.servicemgmt.barcodesettingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.barcodesettingoptions.dto.BarcodeLabelPresetDTO;
import org.sspd.servicemgmt.barcodesettingoptions.service.BarcodeLabelPresetService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/barcode-label-presets")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BarcodeLabelPresetController {

    private final BarcodeLabelPresetService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BarcodeLabelPresetDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Presets", service.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BarcodeLabelPresetDTO>> create(@RequestBody BarcodeLabelPresetDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Created", service.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BarcodeLabelPresetDTO>> update(@PathVariable Integer id,
                                                                      @RequestBody BarcodeLabelPresetDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Updated", service.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Deleted", null));
    }
}
