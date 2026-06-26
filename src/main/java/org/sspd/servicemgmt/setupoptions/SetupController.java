package org.sspd.servicemgmt.setupoptions;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;

@RestController
@RequestMapping("/api/v1/setup")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SetupController {

    private final SetupService setupService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SetupStatusDTO>> getStatus() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Setup status", setupService.getStatus()));
    }

    @PostMapping("/initialize")
    public ResponseEntity<ApiResponse<Void>> initialize(@RequestBody SetupInitDTO dto) {
        setupService.initialize(dto);
        return ResponseEntity.ok(new ApiResponse<>(true, "Setup complete", null));
    }
}
