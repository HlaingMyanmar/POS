package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.controller.PurchaseReturnController;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnAttachmentDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service.PurchaseReturnService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PurchaseReturnAttachmentAuthorizationTest {
    private static final String UPDATE_PERMISSION =
            "hasAuthority('CAN_ACCESS_PURCHASE_RETURN_UPDATE')";

    @Test
    void controllerAndServiceAttachmentMutationsRequireUpdatePermission() throws Exception {
        assertGuard(PurchaseReturnController.class.getMethod(
                "addAttachment", Integer.class, PurchaseReturnAttachmentDTO.class));
        assertGuard(PurchaseReturnController.class.getMethod(
                "deleteAttachment", Integer.class, Integer.class));
        assertGuard(PurchaseReturnService.class.getMethod(
                "addAttachment", Integer.class, PurchaseReturnAttachmentDTO.class));
        assertGuard(PurchaseReturnService.class.getMethod(
                "deleteAttachment", Integer.class, Integer.class));
    }

    private static void assertGuard(Method method) {
        PreAuthorize guard = method.getAnnotation(PreAuthorize.class);
        assertNotNull(guard, () -> method + " must have a method-security guard");
        assertEquals(UPDATE_PERMISSION, guard.value());
    }
}
