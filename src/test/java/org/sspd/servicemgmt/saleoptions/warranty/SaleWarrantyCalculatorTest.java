package org.sspd.servicemgmt.saleoptions.warranty;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SaleWarrantyCalculatorTest {

    @Test
    void snapshotUsesSaleDateNotLaterProductChange() {
        var snap = SaleWarrantyCalculator.snapshot(12, LocalDate.of(2026, 9, 8));
        assertEquals(12, snap.months());
        assertEquals(LocalDate.of(2026, 9, 8), snap.startDate());
        assertEquals(LocalDate.of(2027, 9, 8), snap.endDate());
    }

    @Test
    void zeroMonthsCreatesNoWarranty() {
        var snap = SaleWarrantyCalculator.snapshot(0, LocalDate.of(2026, 9, 8));
        assertEquals(0, snap.months());
        assertNull(snap.startDate());
        assertNull(snap.endDate());
        assertEquals("NONE", SaleWarrantyCalculator.status(0, null, LocalDate.of(2026, 9, 8)));
    }

    @Test
    void remainingAndStatusAreBasedOnFrozenEndDate() {
        LocalDate start = LocalDate.of(2026, 9, 8);
        LocalDate end = start.plusMonths(12);
        assertEquals("ACTIVE", SaleWarrantyCalculator.status(12, end, start));
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(start, end),
                SaleWarrantyCalculator.daysRemaining(12, end, start));
        assertEquals("ACTIVE", SaleWarrantyCalculator.status(12, end, end));
        assertEquals(0, SaleWarrantyCalculator.daysRemaining(12, end, end));
        assertEquals("EXPIRED", SaleWarrantyCalculator.status(12, end, end.plusDays(1)));
        assertEquals(0, SaleWarrantyCalculator.daysRemaining(12, end, end.plusDays(1)));
    }
}
