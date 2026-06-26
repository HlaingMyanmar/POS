package org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.dto.ManufacturingFormulaDTO;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.service.ManufacturingFormulaService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manufacturing/formulas")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ManufacturingFormulaController {

    private final ManufacturingFormulaService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ManufacturingFormulaDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", service.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ManufacturingFormulaDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", service.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ManufacturingFormulaDTO>> create(@RequestBody ManufacturingFormulaDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "ဖန်တီးပြီး", service.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ManufacturingFormulaDTO>> update(@PathVariable Integer id, @RequestBody ManufacturingFormulaDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "မွမ်းမံပြီး", service.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "ဖျက်ပြီး", null));
    }
}
