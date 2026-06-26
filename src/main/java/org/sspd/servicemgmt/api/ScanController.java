package org.sspd.servicemgmt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scan")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ScanController {

    private final SimpMessagingTemplate messagingTemplate;

    public record ScanRequest(String barcode) {}

    @PostMapping
    public ApiResponse<String> scan(@RequestBody ScanRequest req) {
        String code = req.barcode() == null ? "" : req.barcode().trim();
        if (code.isEmpty()) return new ApiResponse<>(false, "Empty barcode", null);
        messagingTemplate.convertAndSend("/topic/barcode-scan", code);
        return new ApiResponse<>(true, "Broadcasted", code);
    }
}
