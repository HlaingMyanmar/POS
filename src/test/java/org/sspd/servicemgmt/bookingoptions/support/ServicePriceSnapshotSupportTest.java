package org.sspd.servicemgmt.bookingoptions.support;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServicePriceSnapshotSupportTest {

    @Test
    void fixedWhenPositivePriceWithoutRange() {
        ServiceItem item = ServiceItem.builder().price(new BigDecimal("15000")).build();
        assertEquals(ServicePriceSnapshotSupport.FIXED, ServicePriceSnapshotSupport.resolvePriceType(item));
        assertEquals(0, new BigDecimal("15000").compareTo(
                ServicePriceSnapshotSupport.resolveDisplayPrice(item, ServicePriceSnapshotSupport.FIXED)));
        assertEquals(ServicePriceSnapshotSupport.ESTIMATE_NOT_REQUIRED,
                ServicePriceSnapshotSupport.defaultEstimateStatus(ServicePriceSnapshotSupport.FIXED));
    }

    @Test
    void startingFromWhenMinPriceSet() {
        ServiceItem item = ServiceItem.builder()
                .price(new BigDecimal("10000"))
                .minPrice(new BigDecimal("10000"))
                .maxPrice(new BigDecimal("30000"))
                .build();
        assertEquals(ServicePriceSnapshotSupport.STARTING_FROM, ServicePriceSnapshotSupport.resolvePriceType(item));
        assertEquals(ServicePriceSnapshotSupport.ESTIMATE_PENDING,
                ServicePriceSnapshotSupport.defaultEstimateStatus(ServicePriceSnapshotSupport.STARTING_FROM));
    }

    @Test
    void inspectionWhenZeroPrice() {
        ServiceItem item = ServiceItem.builder().price(BigDecimal.ZERO).build();
        assertEquals(ServicePriceSnapshotSupport.INSPECTION_REQUIRED,
                ServicePriceSnapshotSupport.resolvePriceType(item));
        assertNull(ServicePriceSnapshotSupport.resolveDisplayPrice(
                item, ServicePriceSnapshotSupport.INSPECTION_REQUIRED));
    }
}
