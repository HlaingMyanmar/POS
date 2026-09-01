package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurchaseReturnMapperNullDefaultsTest {

    @Test
    void toEntityMapsNullShippingFieldsToNotNullDefaults() {
        PurchaseReturnDTO dto = new PurchaseReturnDTO();
        dto.setReason("Damaged");
        dto.setShippingCostAmount(null);
        dto.setShippingPayerResponsibility(null);
        dto.setCompanyShippingPortion(null);
        dto.setSupplierShippingPortion(null);
        dto.setShippingAllocationMethod(null);

        PurchaseReturn entity = PurchaseReturnMapper.INSTANCE.toEntity(dto);

        assertEquals(BigDecimal.ZERO, entity.getShippingCostAmount());
        assertEquals("COMPANY", entity.getShippingPayerResponsibility());
        assertEquals(BigDecimal.ZERO, entity.getCompanyShippingPortion());
        assertEquals(BigDecimal.ZERO, entity.getSupplierShippingPortion());
        assertEquals("VALUE", entity.getShippingAllocationMethod());
    }
}
