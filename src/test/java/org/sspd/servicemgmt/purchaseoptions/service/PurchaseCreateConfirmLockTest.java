package org.sspd.servicemgmt.purchaseoptions.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;
import org.sspd.servicemgmt.purchaseoptions.mapper.PurchaseMapper;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseCreateConfirmLockTest {

    @Test
    void confirmedCreateLocksSupplierBeforePersisting() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        PurchaseMapper mapper = mock(PurchaseMapper.class);
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(Supplier.builder().id(3).name("ACME").build()));
        when(staff.findById(9)).thenReturn(Optional.of(Staff.builder().id(9).build()));
        when(mapper.toEntity(org.mockito.ArgumentMatchers.any(PurchaseDTO.class))).thenReturn(new Purchase());

        PurchaseService service = construct(Map.of(
                PurchaseRepository.class, purchases,
                SupplierRepository.class, suppliers,
                StaffRepository.class, staff,
                PurchaseMapper.class, mapper));

        PurchaseDTO dto = new PurchaseDTO();
        dto.setSupplierId(3);
        dto.setStaffId(9);
        dto.setStatus(PurchaseStatus.CONFIRMED.name());
        dto.setDetails(List.of());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.save(dto));
        verify(suppliers).findByIdForUpdate(3);
        verify(suppliers, never()).findById(3);
        verify(purchases, never()).save(org.mockito.ArgumentMatchers.any());
        assertTrue(thrown.getMessage() != null);
    }

    @Test
    void confirmDraftLocksSupplierThenPurchase() throws Exception {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Supplier supplier = Supplier.builder().id(3).name("ACME").build();
        Purchase purchase = Purchase.builder()
                .id(11)
                .status(PurchaseStatus.DRAFT)
                .supplier(supplier)
                .staff(Staff.builder().id(9).build())
                .details(List.of())
                .build();
        when(purchases.findSupplierIdById(11)).thenReturn(Optional.of(3));
        when(suppliers.findByIdForUpdate(3)).thenReturn(Optional.of(supplier));
        when(purchases.findByIdForUpdate(11)).thenReturn(Optional.of(purchase));

        PurchaseService service = construct(Map.of(
                PurchaseRepository.class, purchases,
                SupplierRepository.class, suppliers,
                EntityManager.class, entityManager));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.confirmDraft(11, null));
        assertTrue(thrown.getMessage().toLowerCase().contains("detail"));
        org.mockito.InOrder order = inOrder(purchases, suppliers, entityManager);
        order.verify(purchases).findSupplierIdById(11);
        order.verify(suppliers).findByIdForUpdate(3);
        order.verify(purchases).findByIdForUpdate(11);
        order.verify(entityManager).refresh(purchase, LockModeType.PESSIMISTIC_WRITE);
        verify(purchases, never()).findById(11);
    }

    private PurchaseService construct(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseService) constructor.newInstance(args);
    }
}
