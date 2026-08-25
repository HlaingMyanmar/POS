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
        Purchase confirmed = Purchase.builder().id(41).status(PurchaseStatus.CONFIRMED).build();
        when(repository.findById(41)).thenReturn(Optional.of(confirmed));
        PurchaseService service = constructService(repository);
        PurchaseDTO dto = new PurchaseDTO();
        dto.setSupplierId(99);
        dto.setStaffId(88);

        assertThrows(IllegalStateException.class, () -> service.update(41, dto));
    }

    private PurchaseService constructService(PurchaseRepository repository) throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes())
                .map(type -> type == PurchaseRepository.class ? repository : mock(type))
                .toArray();
        return (PurchaseService) constructor.newInstance(args);
    }
}
