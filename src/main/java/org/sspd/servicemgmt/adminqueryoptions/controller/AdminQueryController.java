package org.sspd.servicemgmt.adminqueryoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryDefinitionDTO;
import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryExecuteRequest;
import org.sspd.servicemgmt.adminqueryoptions.dto.AdminQueryResultDTO;
import org.sspd.servicemgmt.adminqueryoptions.config.AdminQueryProperties;
import org.sspd.servicemgmt.adminqueryoptions.service.AdminQueryDisabledException;
import org.sspd.servicemgmt.adminqueryoptions.service.AdminQueryService;
import org.sspd.servicemgmt.api.ApiResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin-queries")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminQueryController {

    private final AdminQueryService adminQueryService;
    private final AdminQueryProperties adminQueryProperties;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> status() {
        return ResponseEntity.ok(new ApiResponse<>(
                true,
                "Admin query feature status",
                Map.of("enabled", adminQueryProperties.isEnabled())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CAN_ACCESS_ADMIN_QUERY_READ')")
    public ResponseEntity<ApiResponse<List<AdminQueryDefinitionDTO>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Admin queries", adminQueryService.listQueries()));
    }

    @PostMapping("/{id}/run")
    @PreAuthorize("hasAuthority('CAN_ACCESS_ADMIN_QUERY_READ')")
    public ResponseEntity<ApiResponse<AdminQueryResultDTO>> run(@PathVariable String id) {
        return wrap(() -> adminQueryService.runQuery(id));
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_ADMIN_QUERY_READ', 'CAN_ACCESS_ADMIN_QUERY_WRITE')")
    public ResponseEntity<ApiResponse<AdminQueryResultDTO>> execute(@RequestBody AdminQueryExecuteRequest request) {
        return wrap(() -> adminQueryService.executeCustom(request));
    }

    private ResponseEntity<ApiResponse<AdminQueryResultDTO>> wrap(QueryRunner runner) {
        try {
            AdminQueryResultDTO result = runner.run();
            return ResponseEntity.ok(new ApiResponse<>(true, "Query completed", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (AdminQueryDisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @ExceptionHandler(AdminQueryDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> disabled(AdminQueryDisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(false, e.getMessage(), null));
    }

    @FunctionalInterface
    private interface QueryRunner {
        AdminQueryResultDTO run() throws Exception;
    }
}
