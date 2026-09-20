package org.sspd.servicemgmt.supplieroptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.supplieroptions.dto.SupplierDTO;
import org.sspd.servicemgmt.supplieroptions.mapper.SupplierMapper;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupplierServiceCodeGenerationTest {

    @Test
    void saveAssignsCodeFromGeneratedIdNotLastRowCount() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        SupplierMapper mapper = mock(SupplierMapper.class);
        SupplierService service = construct(Map.of(SupplierRepository.class, suppliers, SupplierMapper.class, mapper));

        when(suppliers.existsByName("ACME")).thenReturn(false);
        when(mapper.toEntity(any(SupplierDTO.class))).thenReturn(new Supplier());
        when(suppliers.save(any(Supplier.class))).thenAnswer(inv -> {
            Supplier saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(12);
            return saved;
        });
        when(mapper.toDto(any(Supplier.class))).thenAnswer(inv -> {
            Supplier saved = inv.getArgument(0);
            SupplierDTO dto = new SupplierDTO();
            dto.setId(saved.getId());
            dto.setCode(saved.getCode());
            return dto;
        });

        SupplierDTO request = new SupplierDTO();
        request.setName("ACME");
        SupplierDTO created = service.save(request);

        assertEquals("SUP-012", created.getCode());
        verify(suppliers, never()).findTopByOrderByIdDesc();
    }

    @Test
    void concurrentCreatesWouldGetDistinctCodesFromTheirOwnIds() {
        SupplierRepository suppliers = mock(SupplierRepository.class);
        SupplierMapper mapper = mock(SupplierMapper.class);
        SupplierService service = construct(Map.of(SupplierRepository.class, suppliers, SupplierMapper.class, mapper));
        AtomicInteger ids = new AtomicInteger(40);

        when(suppliers.existsByName(any())).thenReturn(false);
        when(mapper.toEntity(any(SupplierDTO.class))).thenAnswer(inv -> new Supplier());
        when(suppliers.save(any(Supplier.class))).thenAnswer(inv -> {
            Supplier saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(ids.getAndIncrement());
            return saved;
        });
        when(mapper.toDto(any(Supplier.class))).thenAnswer(inv -> {
            Supplier saved = inv.getArgument(0);
            SupplierDTO dto = new SupplierDTO();
            dto.setCode(saved.getCode());
            return dto;
        });

        SupplierDTO first = new SupplierDTO();
        first.setName("One");
        SupplierDTO second = new SupplierDTO();
        second.setName("Two");

        assertEquals("SUP-040", service.save(first).getCode());
        assertEquals("SUP-041", service.save(second).getCode());
        verify(suppliers, never()).findTopByOrderByIdDesc();
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
