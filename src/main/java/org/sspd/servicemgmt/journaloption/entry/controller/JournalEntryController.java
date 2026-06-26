package org.sspd.servicemgmt.journaloption.entry.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalEntryService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/journal-entries")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService service;

    // ၁။ Journal Entry အားလုံးကို ပြန်ကြည့်ခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<JournalEntryDTO>>> getAll() {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Journal Entry List", service.findAll())
        );
    }

    // ၂။ ID ဖြင့် တစ်ခုတည်းကို ရှာဖွေခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_READ')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JournalEntryDTO>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Journal Entry Found", service.findById(id))
        );
    }

    // ၃။ Manual Journal Entry အသစ်ရိုက်သွင်းခြင်း
    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<JournalEntryDTO>> create(
            @Valid @RequestBody JournalEntryDTO dto) {
        JournalEntryDTO created = service.save(dto);
        return ResponseEntity.status(201).body(
                new ApiResponse<>(true, "Journal Entry Created Successfully", created)
        );
    }

}
