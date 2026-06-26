package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.reportoptions.service.JournalBackfillService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class JournalBackfillController {

    private final JournalBackfillService backfillService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_CREATE')")
    @PostMapping("/backfill-journals")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> backfillJournals() {
        Map<String, Integer> result = backfillService.backfillAll();
        String msg = String.format("Backfill complete — Sales: %d, Purchases: %d, Expenses: %d",
                result.get("sales"), result.get("purchases"), result.get("expenses"));
        return ResponseEntity.ok(new ApiResponse<>(true, msg, result));
    }
}
