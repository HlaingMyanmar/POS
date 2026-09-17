package org.sspd.servicemgmt.purchaseoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseHighResidualGuardTest {

    @Test
    void duplicateQueryExcludesCurrentDraftAndCountsConfirmedOnly() throws Exception {
        Query query = PurchaseRepository.class.getMethod("countRecentDuplicates",
                        Integer.class, Integer.class, BigDecimal.class, LocalDateTime.class, Integer.class)
                .getAnnotation(Query.class);

        assertTrue(query.value().contains(":excludeId IS NULL OR p.id <> :excludeId"));
        assertTrue(query.value().contains("PurchaseStatus.CONFIRMED"));
    }

    @Test
    void confirmedPurchaseCannotBeReassignedThroughGeneralUpdate() throws Exception {
        PurchaseRepository repository = mock(PurchaseRepository.class);
        org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository suppliers =
                mock(org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository.class);
        jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);
        Purchase confirmed = Purchase.builder().id(41).status(PurchaseStatus.CONFIRMED).build();
        when(repository.findSupplierIdById(41)).thenReturn(Optional.of(1));
        when(suppliers.findByIdForUpdate(1)).thenReturn(Optional.of(
                org.sspd.servicemgmt.supplieroptions.model.Supplier.builder().id(1).build()));
        when(suppliers.findByIdForUpdate(99)).thenReturn(Optional.of(
                org.sspd.servicemgmt.supplieroptions.model.Supplier.builder().id(99).build()));
        when(repository.findByIdForUpdate(41)).thenReturn(Optional.of(confirmed));
        PurchaseService service = constructService(Map.of(
                PurchaseRepository.class, repository,
                org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository.class, suppliers,
                jakarta.persistence.EntityManager.class, entityManager));
        PurchaseDTO dto = new PurchaseDTO();
        dto.setSupplierId(99);
        dto.setStaffId(88);

        assertThrows(IllegalStateException.class, () -> service.update(41, dto));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(repository, suppliers, entityManager);
        order.verify(repository).findSupplierIdById(41);
        order.verify(suppliers).findByIdForUpdate(1);
        order.verify(suppliers).findByIdForUpdate(99);
        order.verify(repository).findByIdForUpdate(41);
    }

    private PurchaseService constructService(Map<Class<?>, Object> overrides) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> overrides.containsKey(type) ? overrides.get(type) : mock(type))
                .toArray();
        return (PurchaseService) constructor.newInstance(args);
    }
}
