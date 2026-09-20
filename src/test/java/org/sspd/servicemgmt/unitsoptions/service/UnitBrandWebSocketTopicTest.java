package org.sspd.servicemgmt.unitsoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.brandoptions.dto.BrandDTO;
import org.sspd.servicemgmt.brandoptions.mapper.BrandMapper;
import org.sspd.servicemgmt.brandoptions.model.Brand;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.brandoptions.service.BrandService;
import org.sspd.servicemgmt.unitsoptions.dto.UnitDTO;
import org.sspd.servicemgmt.unitsoptions.mapper.UnitMapper;
import org.sspd.servicemgmt.unitsoptions.model.Unit;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

import java.util.Optional;

import static org.mockito.Mockito.*;

class UnitBrandWebSocketTopicTest {

    @Test
    void unitMutationsPublishOnlyToUnitTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        UnitMapper mapper = mock(UnitMapper.class);
        UnitRepository units = mock(UnitRepository.class);
        UnitService service = new UnitService(messaging, mapper, units);
        UnitDTO dto = new UnitDTO();
        dto.setUnitName("Each");
        Unit unit = new Unit(7, "Each", null);

        when(mapper.toEntity(dto)).thenReturn(unit);
        when(mapper.toDto(unit)).thenReturn(dto);
        when(units.save(unit)).thenReturn(unit);
        when(units.findById(7L)).thenReturn(Optional.of(unit));

        service.save(dto);
        service.update(7L, dto);
        service.delete(7L);

        verify(messaging).convertAndSend("/topic/unit", "UNIT_CREATED");
        verify(messaging).convertAndSend("/topic/unit", "UNIT_UPDATED");
        verify(messaging).convertAndSend("/topic/unit", "UNIT_DELETE");
        verify(messaging, never()).convertAndSend(eq("/topic/brand"), any(Object.class));
        verifyNoMoreInteractions(messaging);
    }

    @Test
    void brandMutationsRemainOnBrandTopic() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        BrandMapper mapper = mock(BrandMapper.class);
        BrandRepository brands = mock(BrandRepository.class);
        BrandService service = new BrandService(brands, mapper, messaging);
        BrandDTO dto = new BrandDTO();
        dto.setName("Acme");
        Brand brand = new Brand(9, "Acme", true);

        when(mapper.toEntitiy(dto)).thenReturn(brand);
        when(mapper.toDto(brand)).thenReturn(dto);
        when(brands.save(brand)).thenReturn(brand);
        when(brands.findById(9L)).thenReturn(Optional.of(brand));

        service.save(dto);
        service.update(9L, dto);
        service.delete(9L);

        verify(messaging).convertAndSend("/topic/brand", "BRAND_CREATED");
        verify(messaging).convertAndSend("/topic/brand", "BRAND_UPDATED");
        verify(messaging).convertAndSend("/topic/brand", "BRAND_DELETE");
        verify(messaging, never()).convertAndSend(eq("/topic/unit"), any(Object.class));
        verifyNoMoreInteractions(messaging);
    }
}
