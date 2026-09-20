package org.sspd.servicemgmt.purchaseoptions.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseCancelMixedCreditRegressionTest {

    @Test
    void mixedCashAndSupplierCreditCancelJournalBalances() throws Exception {
        JournalWriter writer = mock(JournalWriter.class);
        AccountResolver accounts = mock(AccountResolver.class);
        when(accounts.inventory()).thenReturn(account(22));
        when(accounts.inputTaxReceivable()).thenReturn(account(23));
        when(accounts.withholdingTaxPayable()).thenReturn(account(24));
        when(accounts.payable()).thenReturn(account(25));
        when(accounts.supplierAdvance()).thenReturn(account(11));
        PurchaseService service = construct(Map.of(
                JournalWriter.class, writer,
                AccountResolver.class, accounts));
        Purchase purchase = Purchase.builder()
                .id(5)
                .purchaseCode("PUR-5")
                .supplier(Supplier.builder().id(1).name("Supplier").build())
                .staff(Staff.builder().id(2).build())
                .netAmount(new BigDecimal("100"))
                .taxAmount(BigDecimal.ZERO)
                .withholdingTaxAmount(BigDecimal.ZERO)
                .dueAmount(BigDecimal.ZERO)
                .build();
        PaymentMethod cash = PaymentMethod.builder().id(1).account(account(31)).active(true).build();

        invoke(service, "createCancelJournal",
                new Class<?>[]{Purchase.class, BigDecimal.class, PaymentMethod.class, BigDecimal.class},
                purchase, new BigDecimal("40"), cash, new BigDecimal("60"));

        ArgumentCaptor<JournalEntryDTO> captor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        org.mockito.Mockito.verify(writer).write(captor.capture());
        JournalEntryDTO entry = captor.getValue();
        BigDecimal debit = entry.getDetails().stream().map(d -> d.getDebit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = entry.getDetails().stream().map(d -> d.getCredit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100"), debit);
        assertEquals(new BigDecimal("100"), credit);
        assertEquals(new BigDecimal("40"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(31).equals(d.getAccountId()))
                .map(d -> d.getDebit()).findFirst().orElseThrow());
        assertEquals(new BigDecimal("60"), entry.getDetails().stream()
                .filter(d -> Integer.valueOf(11).equals(d.getAccountId()))
                .map(d -> d.getDebit()).findFirst().orElseThrow());
    }

    @Test
    void cancelRestoresReturnCreditToSourceVoucherNotAdvance() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        Supplier supplier = Supplier.builder().id(1).advanceBalance(new BigDecimal("5.00")).build();
        Purchase source = Purchase.builder().id(8).supplier(supplier).supplierCreditAmount(BigDecimal.ZERO).build();
        when(purchases.findByIdForUpdate(8)).thenReturn(Optional.of(source));
        PurchaseService service = construct(Map.of(PurchaseRepository.class, purchases));
        SupplierCreditApplication application = SupplierCreditApplication.builder()
                .applicationNo("SCA-1")
                .advanceUsed(new BigDecimal("10.00"))
                .returnCreditUsed(new BigDecimal("60.00"))
                .returnCreditSources("8:60.00")
                .build();

        invoke(service, "restoreAppliedSupplierCredit",
                new Class<?>[]{Supplier.class, Integer.class, List.class},
                supplier, 5, List.of(application));

        assertEquals(new BigDecimal("15.00"), supplier.getAdvanceBalance());
        assertEquals(new BigDecimal("60.00"), source.getSupplierCreditAmount());
        assertTrue(Boolean.TRUE.equals(application.getVoided()));
        assertEquals("Purchase cancelled", application.getVoidReason());
    }

    @Test
    void cancelSkipsAlreadyVoidedCreditApplication() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        Supplier supplier = Supplier.builder().id(1).advanceBalance(new BigDecimal("5.00")).build();
        Purchase source = Purchase.builder().id(8).supplier(supplier).supplierCreditAmount(BigDecimal.ZERO).build();
        PurchaseService service = construct(Map.of(PurchaseRepository.class, purchases));
        SupplierCreditApplication application = SupplierCreditApplication.builder()
                .applicationNo("SCA-VOIDED")
                .advanceUsed(new BigDecimal("10.00"))
                .returnCreditUsed(new BigDecimal("60.00"))
                .returnCreditSources("8:60.00")
                .voided(true)
                .build();

        invoke(service, "restoreAppliedSupplierCredit",
                new Class<?>[]{Supplier.class, Integer.class, List.class},
                supplier, 5, List.of(application));

        assertEquals(new BigDecimal("5.00"), supplier.getAdvanceBalance());
        assertEquals(BigDecimal.ZERO, source.getSupplierCreditAmount());
        verify(purchases, never()).findByIdForUpdate(8);
    }

    @Test
    void cancelDoesNotParkLegacyReturnCreditOnAnUnrelatedPurchase() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        Supplier supplier = Supplier.builder().id(1).advanceBalance(BigDecimal.ZERO).build();
        Purchase unrelated = Purchase.builder().id(9).supplier(supplier)
                .supplierCreditAmount(new BigDecimal("12.00")).build();
        PurchaseService service = construct(Map.of(PurchaseRepository.class, purchases));
        SupplierCreditApplication application = SupplierCreditApplication.builder()
                .applicationNo("SCA-LEGACY")
                .advanceUsed(BigDecimal.ZERO)
                .returnCreditUsed(new BigDecimal("60.00"))
                .returnCreditSources(null)
                .build();

        java.lang.reflect.InvocationTargetException thrown = assertThrows(java.lang.reflect.InvocationTargetException.class, () ->
                invoke(service, "restoreAppliedSupplierCredit",
                        new Class<?>[]{Supplier.class, Integer.class, List.class},
                        supplier, 5, List.of(application)));

        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertTrue(thrown.getCause().getMessage().toLowerCase().contains("source"));
        assertEquals(new BigDecimal("12.00"), unrelated.getSupplierCreditAmount());
        verify(purchases, never()).findByIdForUpdate(9);
        verify(purchases, never()).save(unrelated);
    }

    @Test
    void cancelRefreshesLockedPurchaseInsteadOfReusingStaleFindById() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository suppliers =
                mock(org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository.class);
        jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);
        org.sspd.servicemgmt.purchaseoptions.mapper.PurchaseMapper mapper =
                mock(org.sspd.servicemgmt.purchaseoptions.mapper.PurchaseMapper.class);
        Supplier supplier = Supplier.builder().id(1).name("Supplier").build();
        Purchase purchase = Purchase.builder()
                .id(5)
                .status(org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.DRAFT)
                .supplier(supplier)
                .build();
        when(purchases.findSupplierIdById(5)).thenReturn(Optional.of(1));
        when(suppliers.findByIdForUpdate(1)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(5)).thenReturn(Optional.of(purchase));
        when(purchases.save(purchase)).thenReturn(purchase);
        when(mapper.toDto(purchase)).thenReturn(new org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO());

        PurchaseService service = construct(Map.of(
                PurchaseRepository.class, purchases,
                org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository.class, suppliers,
                jakarta.persistence.EntityManager.class, entityManager,
                org.sspd.servicemgmt.purchaseoptions.mapper.PurchaseMapper.class, mapper));
        service.cancel(5, "changed mind", null);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(purchases, suppliers, entityManager);
        order.verify(purchases).findSupplierIdById(5);
        order.verify(suppliers).findByIdForUpdate(1);
        order.verify(purchases).findByIdForUpdate(5);
        order.verify(entityManager).refresh(purchase, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        org.mockito.Mockito.verify(purchases, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
    }

    private PurchaseService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseService) constructor.newInstance(args);
    }

    private void invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private ChartOfAccount account(int id) {
        return ChartOfAccount.builder().id(id).build();
    }
}
