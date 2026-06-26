package org.sspd.servicemgmt.auditoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.auditoptions.dto.AuditLogDTO;
import org.sspd.servicemgmt.auditoptions.service.AuditLogService;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService service;

    @PreAuthorize("hasAuthority('CAN_ACCESS_AUDIT_LOG_READ')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AuditLogDTO>>> search(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "50") int    size,
            @RequestParam(defaultValue = "")   String actor,
            @RequestParam(defaultValue = "")   String action,
            @RequestParam(defaultValue = "")   String module,
            @RequestParam(defaultValue = "")   String dateFrom,
            @RequestParam(defaultValue = "")   String dateTo) {

        Page<AuditLogDTO> result = service.search(page, size, actor, action, module, dateFrom, dateTo);
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", result));
    }
}
