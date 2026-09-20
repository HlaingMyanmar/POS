package org.sspd.servicemgmt.supplieroptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierPaymentRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierServiceDeleteGuardTest {

    @Test
    void deleteRejectsNegativeCurrentBalance() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(Supplier.builder()
                .id(3).currentBalance(new BigDecimal("-40")).advanceBalance(BigDecimal.ZERO).build()));
        SupplierService service = construct(Map.of(SupplierRepository.class, suppliers));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.delete(3));
        assertTrue(thrown.getMessage().toLowerCase().contains("outstanding balance"));
        verify(suppliers, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteRejectsAdvanceBalanceEvenWhenCurrentIsZero() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(Supplier.builder()
                .id(3).currentBalance(BigDecimal.ZERO).advanceBalance(new BigDecimal("25")).build()));
        SupplierService service = construct(Map.of(SupplierRepository.class, suppliers));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.delete(3));
        assertTrue(thrown.getMessage().toLowerCase().contains("advance"));
        verify(suppliers, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteRejectsPurchaseHistory() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(Supplier.builder()
                .id(3).currentBalance(BigDecimal.ZERO).advanceBalance(BigDecimal.ZERO).build()));
        when(purchases.existsBySupplier_Id(3)).thenReturn(true);
        SupplierService service = construct(Map.of(
                SupplierRepository.class, suppliers,
                PurchaseRepository.class, purchases));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.delete(3));
        assertTrue(thrown.getMessage().toLowerCase().contains("purchase history"));
        verify(suppliers, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteRejectsPaymentHistory() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(Supplier.builder()
                .id(3).currentBalance(BigDecimal.ZERO).advanceBalance(BigDecimal.ZERO).build()));
        when(purchases.existsBySupplier_Id(3)).thenReturn(false);
        when(payments.existsBySupplier_Id(3)).thenReturn(true);
        SupplierService service = construct(Map.of(
                SupplierRepository.class, suppliers,
                PurchaseRepository.class, purchases,
                SupplierPaymentRepository.class, payments));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.delete(3));
        assertTrue(thrown.getMessage().toLowerCase().contains("payment history"));
        verify(suppliers, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteRejectsReturnHistory() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(Supplier.builder()
                .id(3).currentBalance(BigDecimal.ZERO).advanceBalance(BigDecimal.ZERO).build()));
        when(purchases.existsBySupplier_Id(3)).thenReturn(false);
        when(payments.existsBySupplier_Id(3)).thenReturn(false);
        when(returns.existsByPurchase_Supplier_Id(3)).thenReturn(true);
        SupplierService service = construct(Map.of(
                SupplierRepository.class, suppliers,
                PurchaseRepository.class, purchases,
                SupplierPaymentRepository.class, payments,
                PurchaseReturnRepository.class, returns));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.delete(3));
        assertTrue(thrown.getMessage().toLowerCase().contains("return history"));
        verify(suppliers, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteAllowsSupplierWithNoBalanceAndNoHistory() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierPaymentRepository payments = mock(SupplierPaymentRepository.class);
        PurchaseReturnRepository returns = mock(PurchaseReturnRepository.class);
        Supplier supplier = Supplier.builder()
                .id(3).currentBalance(BigDecimal.ZERO).advanceBalance(BigDecimal.ZERO).build();
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.existsBySupplier_Id(3)).thenReturn(false);
        when(payments.existsBySupplier_Id(3)).thenReturn(false);
        when(returns.existsByPurchase_Supplier_Id(3)).thenReturn(false);
        SupplierService service = construct(Map.of(
                SupplierRepository.class, suppliers,
                PurchaseRepository.class, purchases,
                SupplierPaymentRepository.class, payments,
                PurchaseReturnRepository.class, returns));

        service.delete(3);
        verify(suppliers).delete(supplier);
    }

    private SupplierService construct(Map<Class<?>, Object> overrides) {
        try {
            Constructor<?> constructor = Arrays.stream(SupplierService.class.getConstructors())
                    .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
            Object[] args = Arrays.stream(constructor.getParameterTypes())
                    .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                    .toArray();
            return (SupplierService) constructor.newInstance(args);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
