package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PurchaseReturnShippingAllocationTest {

    @Test
    void valueAllocationAssignsRoundingResidualToLastDetail() throws Exception {
        PurchaseReturnService service = construct();
        PurchaseReturn entity = purchaseReturn("100.00", detail("33.33", 1), detail("66.67", 2));
        PurchaseReturnDTO dto = shipping("10.00", "SHARED", "4.00", "6.00", "VALUE");

        service.configureShipping(entity, dto);

        assertEquals(new BigDecimal("3.33"), entity.getDetails().get(0).getAllocatedShippingCost());
        assertEquals(new BigDecimal("6.67"), entity.getDetails().get(1).getAllocatedShippingCost());
        assertEquals(new BigDecimal("94.00"), service.settlementValue(entity));
    }

    @Test
    void quantityAllocationUsesReturnedUnits() throws Exception {
        PurchaseReturnService service = construct();
        PurchaseReturn entity = purchaseReturn("100.00", detail("50.00", 1), detail("50.00", 3));

        service.configureShipping(entity, shipping("8.00", "COMPANY", "8.00", "0.00", "QUANTITY"));

        assertEquals(new BigDecimal("2.00"), entity.getDetails().get(0).getAllocatedShippingCost());
        assertEquals(new BigDecimal("6.00"), entity.getDetails().get(1).getAllocatedShippingCost());
    }

    @Test
    void manualAllocationAndPortionTotalsAreValidated() throws Exception {
        PurchaseReturnService service = construct();
        PurchaseReturnDetail first = detail("50.00", 1);
        PurchaseReturnDetail second = detail("50.00", 1);
        first.setAllocatedShippingCost(new BigDecimal("3.00"));
        second.setAllocatedShippingCost(new BigDecimal("4.00"));
        PurchaseReturn entity = purchaseReturn("100.00", first, second);

        assertThrows(IllegalArgumentException.class,
                () -> service.configureShipping(entity, shipping("8.00", "SHARED", "4.00", "4.00", "MANUAL")));
        assertThrows(IllegalArgumentException.class,
                () -> service.configureShipping(entity, shipping("8.00", "SHARED", "3.00", "4.00", "VALUE")));
    }

    private PurchaseReturnDTO shipping(String total, String payer, String company, String supplier, String method) {
        PurchaseReturnDTO dto = new PurchaseReturnDTO();
        dto.setShippingCostAmount(new BigDecimal(total));
        dto.setShippingPayerResponsibility(payer);
        dto.setCompanyShippingPortion(new BigDecimal(company));
        dto.setSupplierShippingPortion(new BigDecimal(supplier));
        dto.setShippingAllocationMethod(method);
        return dto;
    }

    private PurchaseReturn purchaseReturn(String total, PurchaseReturnDetail... details) {
        return PurchaseReturn.builder().totalReturnAmount(new BigDecimal(total))
                .details(List.of(details)).build();
    }

    private PurchaseReturnDetail detail(String subtotal, int qty) {
        return PurchaseReturnDetail.builder().subtotal(new BigDecimal(subtotal)).qty(qty)
                .allocatedShippingCost(BigDecimal.ZERO).build();
    }

    private PurchaseReturnService construct() throws Exception {
        Constructor<?> constructor = Arrays.stream(PurchaseReturnService.class.getConstructors())
                .max(java.util.Comparator.comparingInt(Constructor::getParameterCount)).orElseThrow();
        Object[] args = Arrays.stream(constructor.getParameterTypes()).map(org.mockito.Mockito::mock).toArray();
        return (PurchaseReturnService) constructor.newInstance(args);
    }
}
