package org.sspd.servicemgmt.stockoptions.manufacturingoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.dto.ManufacturingOrderDTO;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.service.ManufacturingService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manufacturing")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ManufacturingController {

    private final ManufacturingService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ManufacturingOrderDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", service.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ManufacturingOrderDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", service.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ManufacturingOrderDTO>> create(@RequestBody ManufacturingOrderDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "ဖန်တီးပြီး", service.create(dto)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ManufacturingOrderDTO>> update(@PathVariable Integer id, @RequestBody ManufacturingOrderDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "မွမ်းမံပြီး", service.update(id, dto)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ManufacturingOrderDTO>> complete(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "ပြီးဆုံးပြီ", service.complete(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ManufacturingOrderDTO>> cancel(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "ပယ်ဖျက်ပြီး", service.cancel(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "ဖျက်ပြီး", null));
    }
}
