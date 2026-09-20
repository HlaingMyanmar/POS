package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto.SupplierCreditApplyRequest;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto.SupplierPaymentRequest;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierPaymentDraftGuardTest {

    @Test
    void manualAllocationRejectsDraftPurchase() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        Supplier supplier = Supplier.builder().id(3).build();
        Purchase draft = Purchase.builder()
                .id(11)
                .purchaseCode("PO-DRAFT")
                .supplier(supplier)
                .status(PurchaseStatus.DRAFT)
                .dueAmount(new BigDecimal("100"))
                .build();
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(draft));

        SupplierPaymentService service = construct(purchases, mock(SupplierRepository.class), mock(StaffRepository.class));
        SupplierPaymentRequest request = new SupplierPaymentRequest();
        request.setAmount(new BigDecimal("100"));
        SupplierPaymentRequest.Allocation allocation = new SupplierPaymentRequest.Allocation();
        allocation.setPurchaseId(11);
        allocation.setAmount(new BigDecimal("100"));
        request.setAllocations(List.of(allocation));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> invoke(service, "resolveAllocations",
                        new Class<?>[]{SupplierPaymentRequest.class, Supplier.class},
                        request, supplier));
        assertTrue(thrown.getMessage().toLowerCase().contains("draft"));
    }

    @Test
    void applyCreditRejectsDraftPurchase() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        Supplier supplier = Supplier.builder().id(3).advanceBalance(new BigDecimal("50")).build();
        Purchase draft = Purchase.builder()
                .id(11)
                .purchaseCode("PO-DRAFT")
                .supplier(supplier)
                .status(PurchaseStatus.DRAFT)
                .dueAmount(new BigDecimal("50"))
                .build();
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(draft));
        when(staff.existsById(9)).thenReturn(true);

        SupplierPaymentService service = construct(purchases, suppliers, staff);
        SupplierCreditApplyRequest request = new SupplierCreditApplyRequest();
        request.setSupplierId(3);
        request.setPurchaseId(11);
        request.setStaffId(9);
        request.setAmount(new BigDecimal("50"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.applyCredit(request));
        assertTrue(thrown.getMessage().toLowerCase().contains("draft"));
    }

    private static SupplierPaymentService construct(PurchaseRepository purchases,
                                                    SupplierRepository suppliers,
                                                    StaffRepository staff) throws Exception {
        Constructor<?> constructor = Arrays.stream(SupplierPaymentService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> {
                    if (type == PurchaseRepository.class) return purchases;
                    if (type == SupplierRepository.class) return suppliers;
                    if (type == StaffRepository.class) return staff;
                    return mock(type);
                })
                .toArray();
        return (SupplierPaymentService) constructor.newInstance(args);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }
    }
}
