package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierCreditApplicationRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierCreditApplicationVoidTest {

    @Test
    void voidRestoresDueAdvanceReturnCreditAndReversesJournals() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        SupplierCreditApplicationRepository applications = mock(SupplierCreditApplicationRepository.class);
        JournalWriter journals = mock(JournalWriter.class);

        Supplier supplier = Supplier.builder().id(3).name("ACME")
                .advanceBalance(new BigDecimal("5"))
                .openingBalance(BigDecimal.ZERO)
                .build();
        Purchase target = Purchase.builder()
                .id(11).purchaseCode("PO-11").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .paidAmount(new BigDecimal("70")).dueAmount(BigDecimal.ZERO)
                .returnAmount(BigDecimal.ZERO).build();
        Purchase source = Purchase.builder()
                .id(8).purchaseCode("PO-8").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED)
                .supplierCreditAmount(BigDecimal.ZERO).build();
        SupplierCreditApplication application = SupplierCreditApplication.builder()
                .id(21).applicationNo("SCA-000021").supplier(supplier).targetPurchase(target)
                .amount(new BigDecimal("70")).advanceUsed(new BigDecimal("10"))
                .returnCreditUsed(new BigDecimal("60")).returnCreditSources("8:60")
                .voided(false).build();

        when(applications.findById(21)).thenReturn(Optional.of(application));
        when(applications.findByIdForUpdate(21)).thenReturn(Optional.of(application));
        when(applications.save(application)).thenReturn(application);
        when(staff.existsById(9)).thenReturn(true);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(suppliers.save(supplier)).thenReturn(supplier);
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(target));
        when(purchases.findByIdForUpdate(8)).thenReturn(Optional.of(source));
        when(purchases.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchases.sumDueAmountBySupplierId(3)).thenReturn(new BigDecimal("70"));
        when(purchases.sumSupplierCreditAmountBySupplierId(3)).thenReturn(new BigDecimal("60"));

        SupplierPaymentService service = construct(purchases, suppliers, staff, applications, journals);
        var result = service.voidCreditApplication(21, "wrong voucher");

        assertTrue(Boolean.TRUE.equals(result.getVoided()));
        assertEquals(new BigDecimal("70"), target.getDueAmount());
        assertEquals(BigDecimal.ZERO, target.getPaidAmount());
        assertEquals(PaymentStatus.Pending, target.getPaymentStatus());
        assertEquals(new BigDecimal("15"), supplier.getAdvanceBalance());
        assertEquals(new BigDecimal("60"), source.getSupplierCreditAmount());
        verify(journals).reverseByReferenceNo("SCA-000021");
        verify(journals).reverseByReferenceNo("SCA-000021-RC");
    }

    @Test
    void voidRejectsAlreadyVoidedApplication() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        SupplierCreditApplicationRepository applications = mock(SupplierCreditApplicationRepository.class);
        JournalWriter journals = mock(JournalWriter.class);

        Supplier supplier = Supplier.builder().id(3).advanceBalance(BigDecimal.ZERO).build();
        Purchase target = Purchase.builder().id(11).purchaseCode("PO-11").supplier(supplier)
                .status(PurchaseStatus.CONFIRMED).paidAmount(new BigDecimal("50")).dueAmount(BigDecimal.ZERO).build();
        SupplierCreditApplication application = SupplierCreditApplication.builder()
                .id(21).applicationNo("SCA-000021").supplier(supplier).targetPurchase(target)
                .amount(new BigDecimal("50")).advanceUsed(new BigDecimal("50"))
                .returnCreditUsed(BigDecimal.ZERO).voided(true).build();
        when(applications.findById(21)).thenReturn(Optional.of(application));
        when(applications.findByIdForUpdate(21)).thenReturn(Optional.of(application));
        when(staff.existsById(9)).thenReturn(true);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(target));

        SupplierPaymentService service = construct(purchases, suppliers, staff, applications, journals);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidCreditApplication(21, "undo"));
        assertTrue(thrown.getMessage().toLowerCase().contains("already voided"));
        verify(journals, never()).reverseByReferenceNo(any());
        assertEquals(new BigDecimal("50"), target.getPaidAmount());
    }

    @Test
    void voidRejectsCancelledTargetPurchase() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        SupplierCreditApplicationRepository applications = mock(SupplierCreditApplicationRepository.class);
        JournalWriter journals = mock(JournalWriter.class);

        Supplier supplier = Supplier.builder().id(3).advanceBalance(BigDecimal.ZERO).build();
        Purchase target = Purchase.builder().id(11).purchaseCode("PO-11").supplier(supplier)
                .status(PurchaseStatus.CANCELLED).paidAmount(BigDecimal.ZERO).dueAmount(BigDecimal.ZERO).build();
        SupplierCreditApplication application = SupplierCreditApplication.builder()
                .id(21).applicationNo("SCA-000021").supplier(supplier).targetPurchase(target)
                .amount(new BigDecimal("50")).advanceUsed(new BigDecimal("50"))
                .returnCreditUsed(BigDecimal.ZERO).voided(false).build();
        when(applications.findById(21)).thenReturn(Optional.of(application));
        when(applications.findByIdForUpdate(21)).thenReturn(Optional.of(application));
        when(staff.existsById(9)).thenReturn(true);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(target));

        SupplierPaymentService service = construct(purchases, suppliers, staff, applications, journals);
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.voidCreditApplication(21, "undo"));
        assertTrue(thrown.getMessage().toLowerCase().contains("cancelled"));
        verify(journals, never()).reverseByReferenceNo(any());
    }

    private static SupplierPaymentService construct(PurchaseRepository purchases,
                                                    SupplierRepository suppliers,
                                                    StaffRepository staff,
                                                    SupplierCreditApplicationRepository applications,
                                                    JournalWriter journals) throws Exception {
        Constructor<?> constructor = Arrays.stream(SupplierPaymentService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == PurchaseRepository.class) return purchases;
                    if (type == SupplierRepository.class) return suppliers;
                    if (type == StaffRepository.class) return staff;
                    if (type == SupplierCreditApplicationRepository.class) return applications;
                    if (type == JournalWriter.class) return journals;
                    return mock(type);
                })
                .toArray();
        return (SupplierPaymentService) constructor.newInstance(args);
    }
}
