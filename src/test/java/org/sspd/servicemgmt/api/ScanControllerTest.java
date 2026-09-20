package org.sspd.servicemgmt.api;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScanControllerTest {
    private static final String PAIRING_TOKEN = "scanner-pairing-token-at-least-32-chars";

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final ScanController controller = new ScanController(messaging);

    ScanControllerTest() {
        ReflectionTestUtils.setField(controller, "scannerPairingToken", PAIRING_TOKEN);
    }

    @Test
    void anonymousAndIncorrectTokensCannotBroadcast() {
        var request = new ScanController.ScanRequest("ABC-123");

        assertThrows(AccessDeniedException.class, () -> controller.scan(request, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.scan(request, "wrong-token", null));
        verifyNoInteractions(messaging);
    }

    @Test
    void pairedScannerCanBroadcast() {
        var response = controller.scan(
                new ScanController.ScanRequest("  ABC-123  "), PAIRING_TOKEN, null);

        assertTrue(response.isSuccess());
        assertEquals("ABC-123", response.getData());
        verify(messaging).convertAndSend("/topic/barcode-scan", "ABC-123");
    }

    @Test
    void staffWithSaleCreatePermissionCanBroadcastWithoutPairingToken() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "cashier", null,
                List.of(new SimpleGrantedAuthority("CAN_ACCESS_SALE_CREATE")));

        controller.scan(new ScanController.ScanRequest("SKU-1"), null, authentication);

        verify(messaging).convertAndSend("/topic/barcode-scan", "SKU-1");
    }

    @Test
    void authenticatedStaffWithoutPermissionCannotBroadcast() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "viewer", null,
                List.of(new SimpleGrantedAuthority("CAN_ACCESS_SALE_READ")));

        assertThrows(AccessDeniedException.class,
                () -> controller.scan(new ScanController.ScanRequest("SKU-1"), null, authentication));
        verifyNoInteractions(messaging);
    }

    @Test
    void rejectsOversizedOrControlCharacterBarcodes() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.scan(new ScanController.ScanRequest("X".repeat(129)), PAIRING_TOKEN, null));
        assertThrows(IllegalArgumentException.class,
                () -> controller.scan(new ScanController.ScanRequest("ABC\n123"), PAIRING_TOKEN, null));
        verifyNoInteractions(messaging);
    }
}
