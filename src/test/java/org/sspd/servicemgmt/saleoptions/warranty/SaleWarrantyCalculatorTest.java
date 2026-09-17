package org.sspd.servicemgmt.saleoptions.warranty;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void dayTermsCreateExactEndDateWithoutMonths() {
        LocalDate saleDate = LocalDate.of(2026, 9, 8);
        var snap = SaleWarrantyCalculator.snapshot(0, "7 ရက်", saleDate);
        assertEquals(0, snap.months());
        assertEquals(saleDate, snap.startDate());
        assertEquals(saleDate.plusDays(7), snap.endDate());
        assertEquals("7 ရက်", SaleWarrantyCalculator.durationLabel(snap.months(), snap.startDate(), snap.endDate()));
        assertEquals("ACTIVE", SaleWarrantyCalculator.status(0, snap.endDate(), saleDate));
        assertEquals(7, SaleWarrantyCalculator.daysRemaining(0, snap.endDate(), saleDate));
    }

    @Test
    void serialRangeOverridesMissingMonths() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 8);
        var snap = SaleWarrantyCalculator.fromExisting(0, start, end);
        assertTrue(snap.hasCoverage());
        assertEquals(end, snap.endDate());
        assertEquals("ACTIVE", SaleWarrantyCalculator.status(0, end, start));
        assertEquals("7 ရက်", SaleWarrantyCalculator.durationLabel(0, start, end));
    }
}
