package org.sspd.servicemgmt.setupoptions;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SetupStatusDTO>> getStatus() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Setup status", setupService.getStatus()));
    }

    @PostMapping("/initial-admin")
    public ResponseEntity<ApiResponse<Void>> createInitialAdmin(
            @RequestHeader(name = "X-Setup-Token", required = false) String setupToken,
            @RequestBody InitialAdminDTO dto) {
        setupService.createInitialAdministrator(dto, setupToken);
        return ResponseEntity.ok(new ApiResponse<>(true, "Administrator created. Please log in.", null));
    }

    /**
     * Completes first-run company/payment setup. Only ADMINISTRATOR, and only while setup is incomplete.
     */
    @PostMapping("/initialize")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Void>> initialize(@RequestBody SetupInitDTO dto) {
        setupService.initialize(dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Setup complete", null));
    }
}
