package org.sspd.servicemgmt.saleoptions.mapper;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.saleoptions.dto.SaleDTO;
import org.sspd.servicemgmt.saleoptions.model.Sale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SaleMapperNullDefaultsTest {

    @Test
    void toEntityMapsNullVoidedToFalse() {
        SaleDTO dto = new SaleDTO();
        dto.setVoided(null);

        Sale entity = SaleMapper.INSTANCE.toEntity(dto);

        assertEquals(Boolean.FALSE, entity.getVoided());
        assertFalse(Boolean.TRUE.equals(entity.getVoided()));
    }
}
