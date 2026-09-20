package org.sspd.servicemgmt.cashdraweroptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.cashdraweroptions.model.CashDrawerMovement;
import org.sspd.servicemgmt.cashdraweroptions.model.CashDrawerSession;
import org.sspd.servicemgmt.cashdraweroptions.repository.CashDrawerMovementRepository;
import org.sspd.servicemgmt.cashdraweroptions.repository.CashDrawerSessionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashDrawerRefundSessionTest {

    @Test
    void reverseCashRefundDoesNotRestateClosedSession() {
        CashDrawerSessionRepository sessions = mock(CashDrawerSessionRepository.class);
        CashDrawerMovementRepository movements = mock(CashDrawerMovementRepository.class);
        CashDrawerSession original = CashDrawerSession.builder()
                .id(1).openedBy("cashier-a").status("CLOSED")
                .cashRefunds(new BigDecimal("50"))
                .expectedCash(new BigDecimal("50"))
                .build();
        CashDrawerMovement refund = CashDrawerMovement.builder()
                .id(9).session(original).type(CashDrawerService.REFUND_TYPE)
                .amount(new BigDecimal("50")).reversed(false)
                .referenceType("Sale_Return").referenceId(4)
                .build();
        when(movements.findByTypeAndReferenceTypeAndReferenceIdAndReversedFalseOrderByIdAsc(
                CashDrawerService.REFUND_TYPE, "Sale_Return", 4)).thenReturn(List.of(refund));
        when(sessions.findByIdForUpdate(1)).thenReturn(Optional.of(original));
        CashDrawerService service = new CashDrawerService(sessions, movements);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.reverseCashRefund("Sale_Return", 4, new BigDecimal("50")));
        assertTrue(thrown.getMessage().toLowerCase().contains("closed"));
        assertEquals(new BigDecimal("50"), original.getCashRefunds());
        verify(sessions, never()).save(original);
    }

    @Test
    void compensatingCashInHitsCurrentOpenSessionNotClosedOriginal() {
        CashDrawerSessionRepository sessions = mock(CashDrawerSessionRepository.class);
        CashDrawerMovementRepository movements = mock(CashDrawerMovementRepository.class);
        CashDrawerSession original = CashDrawerSession.builder()
                .id(1).openedBy("cashier-a").status("CLOSED")
                .cashRefunds(new BigDecimal("50"))
                .cashIn(BigDecimal.ZERO)
                .build();
        CashDrawerSession current = CashDrawerSession.builder()
                .id(2).openedBy("cashier-b").status("OPEN")
                .cashIn(BigDecimal.ZERO)
                .cashRefunds(BigDecimal.ZERO)
                .build();
        when(movements.existsByTypeAndReferenceTypeAndReferenceIdAndReversedFalse(
                CashDrawerService.IN_TYPE, "Sale_Return", 4)).thenReturn(false);
        when(sessions.findFirstByOpenedByAndStatusOrderByOpenedAtDesc("system", "OPEN"))
                .thenReturn(Optional.of(current));
        when(sessions.findByIdForUpdate(2)).thenReturn(Optional.of(current));
        CashDrawerService service = new CashDrawerService(sessions, movements);

        service.recordCompensatingCashIn(new BigDecimal("50"), "Sale_Return", 4, "Void sale return");

        assertEquals(new BigDecimal("50"), original.getCashRefunds());
        assertEquals(new BigDecimal("50"), current.getCashIn());
        verify(sessions, never()).save(original);
        verify(sessions).save(current);
        verify(movements).save(any(CashDrawerMovement.class));
    }

    @Test
    void referencedRefundRejectsDuplicate() {
        CashDrawerSessionRepository sessions = mock(CashDrawerSessionRepository.class);
        CashDrawerMovementRepository movements = mock(CashDrawerMovementRepository.class);
        when(movements.existsByTypeAndReferenceTypeAndReferenceIdAndReversedFalse(
                CashDrawerService.REFUND_TYPE, "Service", 21)).thenReturn(true);
        CashDrawerService service = new CashDrawerService(sessions, movements);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.recordCashRefund(new BigDecimal("80"), "Service", 21));
        assertTrue(thrown.getMessage().toLowerCase().contains("already"));
        verify(sessions, never()).findFirstByOpenedByAndStatusOrderByOpenedAtDesc(any(), any());
    }

    @Test
    void referencedCashSaleRejectsDuplicate() {
        CashDrawerSessionRepository sessions = mock(CashDrawerSessionRepository.class);
        CashDrawerMovementRepository movements = mock(CashDrawerMovementRepository.class);
        when(movements.existsByTypeAndReferenceTypeAndReferenceIdAndReversedFalse(
                CashDrawerService.SALE_TYPE, "Customer_Payment", 8)).thenReturn(true);
        CashDrawerService service = new CashDrawerService(sessions, movements);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.recordCashSale(new BigDecimal("50"), "Customer_Payment", 8));
        assertTrue(thrown.getMessage().toLowerCase().contains("already"));
        verify(sessions, never()).findFirstByOpenedByAndStatusOrderByOpenedAtDesc(any(), any());
    }

    @Test
    void referencedPurchaseCashOutRejectsDuplicate() {
        CashDrawerSessionRepository sessions = mock(CashDrawerSessionRepository.class);
        CashDrawerMovementRepository movements = mock(CashDrawerMovementRepository.class);
        when(movements.existsByTypeAndReferenceTypeAndReferenceIdAndReversedFalse(
                CashDrawerService.OUT_TYPE, "Supplier_Payment", 8)).thenReturn(true);
        CashDrawerService service = new CashDrawerService(sessions, movements);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.recordPurchaseCashOut(new BigDecimal("50"), "Supplier payment SP-000008",
                        "Supplier_Payment", 8));
        assertTrue(thrown.getMessage().toLowerCase().contains("already"));
        verify(sessions, never()).findFirstByOpenedByAndStatusOrderByOpenedAtDesc(any(), any());
    }

    @Test
    void recordOnOpenSessionFailsWhenNoOpenDrawer() {
        CashDrawerSessionRepository sessions = mock(CashDrawerSessionRepository.class);
        CashDrawerMovementRepository movements = mock(CashDrawerMovementRepository.class);
        when(sessions.findFirstByOpenedByAndStatusOrderByOpenedAtDesc("system", "OPEN"))
                .thenReturn(Optional.empty());
        CashDrawerService service = new CashDrawerService(sessions, movements);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.recordCashSale(new BigDecimal("25"), "Sale", 11));
        assertTrue(thrown.getMessage().toLowerCase().contains("open a cash drawer"));
        verify(movements, never()).save(any());
        verify(sessions, never()).save(any());
    }
}
