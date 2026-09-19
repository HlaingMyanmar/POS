package org.sspd.servicemgmt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
public class ScanController {

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.scanner.pairing-token:}")
    private String scannerPairingToken;

    public record ScanRequest(String barcode) {}

    @PostMapping
    public ApiResponse<String> scan(
            @RequestBody ScanRequest req,
            @RequestHeader(name = "X-Scanner-Token", required = false) String suppliedScannerToken,
            Authentication authentication) {
        requireAuthorizedScanner(suppliedScannerToken, authentication);
        String code = req.barcode() == null ? "" : req.barcode().trim();
        if (code.isEmpty() || code.length() > 128 || code.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Barcode must contain 1 to 128 printable characters.");
        }
        messagingTemplate.convertAndSend("/topic/barcode-scan", code);
        return new ApiResponse<>(true, "Broadcasted", code);
    }

    private void requireAuthorizedScanner(String suppliedScannerToken, Authentication authentication) {
        boolean staffCanCreateSale = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("CAN_ACCESS_SALE_CREATE"));
        if (staffCanCreateSale) return;

        String expected = scannerPairingToken == null ? "" : scannerPairingToken.trim();
        String supplied = suppliedScannerToken == null ? "" : suppliedScannerToken.trim();
        boolean paired = expected.length() >= 32
                && !supplied.isEmpty()
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8));
        if (!paired) {
            throw new AccessDeniedException("Scanner authentication required.");
        }
    }
}
