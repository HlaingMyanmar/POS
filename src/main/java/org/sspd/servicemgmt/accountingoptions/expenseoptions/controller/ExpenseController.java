package org.sspd.servicemgmt.accountingoptions.expenseoptions.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.dto.ExpenseDTO;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.service.ExpenseService;
import org.sspd.servicemgmt.api.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expenses")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_EXPENSE_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseDTO>>> getAll() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Expenses fetched", service.findAll()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_EXPENSE_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Expense detail", service.findById(id)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_EXPENSE_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseDTO>> create(@Valid @RequestBody ExpenseDTO dto) {
        ExpenseDTO created = service.save(dto);
        return ResponseEntity.status(201).body(new ApiResponse<>(true, "Expense created", created));
    }
}
