package org.sspd.servicemgmt.accountingoptions.incomeoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.dto.IncomeDTO;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.service.IncomeService;
import org.sspd.servicemgmt.api.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incomes")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_INCOME_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<IncomeDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Incomes fetched", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_INCOME_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncomeDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Income detail", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_INCOME_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<IncomeDTO>> create(@Valid @RequestBody IncomeDTO dto) {
        IncomeDTO created = service.save(dto);
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Income created", created));
    }
}
