package org.sspd.servicemgmt.servicejoboptions.support;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.servicejoboptions.model.DiscountAllocationMethod;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceLineConfirmationStatus;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceJobSettlementCalculatorTest {

    @Test
    void proRata_allocatesOverallDiscountAcrossLaborAndParts() {
        ServiceJob job = jobWithBalances(new BigDecimal("60000"), new BigDecimal("41000"));
        ServiceJobSettlementBreakdown breakdown = ServiceJobSettlementCalculator.compute(
                job, new BigDecimal("5000"), DiscountAllocationMethod.PRO_RATA, false);

        assertEquals(new BigDecimal("101000"), breakdown.gross());
        assertEquals(0, new BigDecimal("96000").compareTo(breakdown.net()));
        assertEquals(new BigDecimal("57029.70"), breakdown.laborNet());
        assertEquals(new BigDecimal("38970.30"), breakdown.partsNet());
    }

    @Test
    void laborFirst_appliesOverallDiscountToLaborBeforeParts() {
        ServiceJob job = jobWithBalances(new BigDecimal("60000"), new BigDecimal("41000"));
        ServiceJobSettlementBreakdown breakdown = ServiceJobSettlementCalculator.compute(
                job, new BigDecimal("5000"), DiscountAllocationMethod.LABOR_FIRST, false);

        assertEquals(new BigDecimal("55000"), breakdown.laborNet());
        assertEquals(new BigDecimal("41000"), breakdown.partsNet());
        assertEquals(0, new BigDecimal("96000").compareTo(breakdown.net()));
    }

    @Test
    void partsFirst_appliesOverallDiscountToPartsBeforeLabor() {
        ServiceJob job = jobWithBalances(new BigDecimal("60000"), new BigDecimal("41000"));
        ServiceJobSettlementBreakdown breakdown = ServiceJobSettlementCalculator.compute(
                job, new BigDecimal("5000"), DiscountAllocationMethod.PARTS_FIRST, false);

        assertEquals(new BigDecimal("60000"), breakdown.laborNet());
        assertEquals(new BigDecimal("36000"), breakdown.partsNet());
        assertEquals(0, new BigDecimal("96000").compareTo(breakdown.net()));
    }

    @Test
    void laborAndPartsGross_useChargeAndUnitPrice() {
        ServiceJobLine line = ServiceJobLine.builder()
                .qty(1)
                .price(new BigDecimal("50000"))
                .subtotal(new BigDecimal("45000"))
                .discountAmount(new BigDecimal("5000"))
                .confirmationStatus(ServiceLineConfirmationStatus.COMPLETED)
                .build();
        Product product = new Product();
        product.setId(1);
        ServiceJobPart part = ServiceJobPart.builder()
                .product(product)
                .qty(2)
                .unitPrice(new BigDecimal("10000"))
                .discountAmount(new BigDecimal("2000"))
                .subtotal(new BigDecimal("18000"))
                .build();
        ServiceJob job = ServiceJob.builder()
                .lines(java.util.List.of(line))
                .productParts(java.util.List.of(part))
                .build();

        assertEquals(new BigDecimal("50000"), ServiceJobSettlementCalculator.laborGross(job));
        assertEquals(new BigDecimal("20000"), ServiceJobSettlementCalculator.partsGross(job));
    }

    @Test
    void rejectsOverallDiscountAboveGross() {
        ServiceJob job = jobWithBalances(new BigDecimal("10000"), new BigDecimal("5000"));
        assertThrows(IllegalArgumentException.class, () -> ServiceJobSettlementCalculator.compute(
                job, new BigDecimal("20000"), DiscountAllocationMethod.PRO_RATA, false));
    }

    private static ServiceJob jobWithBalances(BigDecimal labor, BigDecimal parts) {
        ServiceJobLine line = ServiceJobLine.builder()
                .qty(1)
                .subtotal(labor)
                .confirmationStatus(ServiceLineConfirmationStatus.COMPLETED)
                .build();
        Product product = new Product();
        product.setId(1);
        ServiceJobPart part = ServiceJobPart.builder()
                .product(product)
                .qty(1)
                .subtotal(parts)
                .build();
        return ServiceJob.builder()
                .lines(java.util.List.of(line))
                .productParts(java.util.List.of(part))
                .build();
    }
}
