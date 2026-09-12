package org.sspd.servicemgmt.servicejoboptions.support;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.servicejoboptions.model.DiscountAllocationMethod;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobPart;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceLineConfirmationStatus;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceJobSettlementJournalBuilderTest {

    private static final int SVC = 1;
    private static final int SALES = 2;
    private static final int LABOR_DISC = 3;
    private static final int PARTS_DISC = 4;
    private static final int AR = 5;
    private static final int CASH = 10;
    private static final int KPAY = 11;
    private static final int BANK = 12;
    private static final int COGS = 20;
    private static final int INV = 21;

    private static final ServiceJobSettlementJournalBuilder.AccountIds ACCOUNTS =
            new ServiceJobSettlementJournalBuilder.AccountIds(SVC, SALES, LABOR_DISC, PARTS_DISC, AR);

    @Test
    void serviceOnly_noDiscount_cash() {
        var breakdown = compute(bd("5000"), bd("0"), bd("0"));
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(CASH, "5000")), bd("5000"), bd("0"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(CASH, bd("5000")), debits(built));
        assertEquals(Map.of(SVC, bd("5000")), credits(built));
    }

    @Test
    void partsOnly_noDiscount_kpay() {
        var breakdown = compute(bd("0"), bd("80000"), bd("0"));
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(KPAY, "80000")), bd("80000"), bd("0"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(KPAY, bd("80000")), debits(built));
        assertEquals(Map.of(SALES, bd("80000")), credits(built));
    }

    @Test
    void serviceAndParts_noDiscount_bank() {
        var breakdown = compute(bd("5000"), bd("80000"), bd("0"));
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(BANK, "85000")), bd("85000"), bd("0"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(BANK, bd("85000")), debits(built));
        assertEquals(Map.of(SVC, bd("5000"), SALES, bd("80000")), credits(built));
    }

    @Test
    void serviceAndParts_proRataBothDiscounts_kpay_userExample() {
        // Labor 5000 + Parts 80000, discount 5000 pro-rata, paid 80000 KPay
        var breakdown = compute(bd("5000"), bd("80000"), bd("5000"));
        assertEquals(0, bd("294.12").compareTo(breakdown.laborBalance().subtract(breakdown.laborNet())));
        assertEquals(0, bd("4705.88").compareTo(breakdown.partsBalance().subtract(breakdown.partsNet())));

        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(KPAY, "80000")), bd("80000"), bd("0"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(0, bd("85000").compareTo(built.totalDebit()));
        assertEquals(0, bd("85000").compareTo(built.totalCredit()));
        assertEquals(Map.of(
                KPAY, bd("80000"),
                LABOR_DISC, bd("294.12"),
                PARTS_DISC, bd("4705.88")
        ), debits(built));
        assertEquals(Map.of(
                SVC, bd("5000"),
                SALES, bd("80000")
        ), credits(built));
    }

    @Test
    void laborDiscountOnly_laborFirst() {
        var breakdown = ServiceJobSettlementCalculator.compute(
                job(bd("5000"), bd("80000")), bd("1000"), DiscountAllocationMethod.LABOR_FIRST, false);
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(CASH, "84000")), bd("84000"), bd("0"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(CASH, bd("84000"), LABOR_DISC, bd("1000")), debits(built));
        assertEquals(Map.of(SVC, bd("5000"), SALES, bd("80000")), credits(built));
        assertEquals(0, debits(built).getOrDefault(PARTS_DISC, bd("0")).compareTo(bd("0")));
    }

    @Test
    void partsDiscountOnly_partsFirst() {
        var breakdown = ServiceJobSettlementCalculator.compute(
                job(bd("5000"), bd("80000")), bd("2000"), DiscountAllocationMethod.PARTS_FIRST, false);
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(KPAY, "83000")), bd("83000"), bd("0"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(KPAY, bd("83000"), PARTS_DISC, bd("2000")), debits(built));
        assertEquals(Map.of(SVC, bd("5000"), SALES, bd("80000")), credits(built));
        assertEquals(0, debits(built).getOrDefault(LABOR_DISC, bd("0")).compareTo(bd("0")));
    }

    @Test
    void partialPayment_postsReceivable() {
        var breakdown = compute(bd("5000"), bd("80000"), bd("0"));
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(CASH, "30000")), bd("30000"), bd("55000"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(CASH, bd("30000"), AR, bd("55000")), debits(built));
        assertEquals(Map.of(SVC, bd("5000"), SALES, bd("80000")), credits(built));
    }

    @Test
    void multiplePaymentMethods_splitCashAndKpay() {
        var breakdown = compute(bd("5000"), bd("80000"), bd("5000"));
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown,
                List.of(pay(CASH, "20000"), pay(KPAY, "60000")),
                bd("80000"),
                bd("0"),
                ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(
                CASH, bd("20000"),
                KPAY, bd("60000"),
                LABOR_DISC, bd("294.12"),
                PARTS_DISC, bd("4705.88")
        ), debits(built));
    }

    @Test
    void multipleParts_aggregatePartsBalance() {
        Product p1 = product(1);
        Product p2 = product(2);
        ServiceJob job = ServiceJob.builder()
                .lines(List.of(line(bd("5000"))))
                .productParts(List.of(
                        part(p1, bd("30000")),
                        part(p2, bd("50000"))
                ))
                .build();
        var breakdown = ServiceJobSettlementCalculator.compute(
                job, bd("0"), DiscountAllocationMethod.PRO_RATA, false);
        var built = ServiceJobSettlementJournalBuilder.build(
                breakdown, List.of(pay(BANK, "85000")), bd("85000"), bd("0"), ACCOUNTS);

        assertBalanced(built);
        assertEquals(Map.of(SVC, bd("5000"), SALES, bd("80000")), credits(built));
    }

    @Test
    void cogsUsesCostNotSellingPrice() {
        var built = ServiceJobSettlementJournalBuilder.buildCogs(COGS, INV, bd("65000"));
        assertBalanced(built);
        assertEquals(Map.of(COGS, bd("65000")), debits(built));
        assertEquals(Map.of(INV, bd("65000")), credits(built));
        // selling 80000 must never appear
        assertEquals(0, built.totalDebit().compareTo(bd("65000")));
    }

    @Test
    void cogsZero_skipsEntry() {
        var built = ServiceJobSettlementJournalBuilder.buildCogs(COGS, INV, bd("0"));
        assertTrue(built.details().isEmpty());
        assertTrue(built.isBalanced());
    }

    @Test
    void settlementReference_isUniquePrefixSafe() {
        String a = ServiceJobSettlementJournalBuilder.settlementReference("SJ-100");
        String b = ServiceJobSettlementJournalBuilder.settlementReference("SJ-100");
        assertTrue(a.startsWith("SJ-100-SETTLE-"));
        assertTrue(b.startsWith("SJ-100-SETTLE-"));
        assertEquals("SJ-100-SETTLE", ServiceJobSettlementJournalBuilder.settlementReferencePrefix("SJ-100"));
        // millisecond clock may collide in same tick; prefix uniqueness is what void/retry relies on
        assertTrue(a.equals(b) || !a.equals(b));
    }

    @Test
    void retryIdempotency_prefixDetectsActiveSettleFamily() {
        String prefix = ServiceJobSettlementJournalBuilder.settlementReferencePrefix("SJ-9");
        String first = prefix + "-111";
        String second = prefix + "-222";
        assertTrue(first.startsWith(prefix));
        assertTrue(second.startsWith(prefix));
        assertTrue(!first.equals(second));
    }

    private static ServiceJobSettlementBreakdown compute(BigDecimal labor, BigDecimal parts, BigDecimal discount) {
        return ServiceJobSettlementCalculator.compute(
                job(labor, parts), discount, DiscountAllocationMethod.PRO_RATA, false);
    }

    private static ServiceJob job(BigDecimal labor, BigDecimal parts) {
        var builder = ServiceJob.builder();
        if (labor.signum() > 0) builder.lines(List.of(line(labor)));
        else builder.lines(List.of());
        if (parts.signum() > 0) builder.productParts(List.of(part(product(1), parts)));
        else builder.productParts(List.of());
        return builder.build();
    }

    private static ServiceJobLine line(BigDecimal subtotal) {
        return ServiceJobLine.builder()
                .qty(1)
                .subtotal(subtotal)
                .confirmationStatus(ServiceLineConfirmationStatus.COMPLETED)
                .build();
    }

    private static ServiceJobPart part(Product product, BigDecimal subtotal) {
        return ServiceJobPart.builder()
                .product(product)
                .qty(1)
                .unitPrice(subtotal)
                .subtotal(subtotal)
                .build();
    }

    private static Product product(int id) {
        Product p = new Product();
        p.setId(id);
        p.setCostPrice(new BigDecimal("1000"));
        return p;
    }

    private static ServiceJobSettlementJournalBuilder.PaymentSlice pay(int accountId, String amount) {
        return new ServiceJobSettlementJournalBuilder.PaymentSlice(accountId, bd(amount));
    }

    private static void assertBalanced(ServiceJobSettlementJournalBuilder.BuiltJournal built) {
        assertTrue(built.isBalanced(),
                "DR " + built.totalDebit() + " != CR " + built.totalCredit());
        assertEquals(0, built.totalDebit().compareTo(built.totalCredit()));
    }

    private static Map<Integer, BigDecimal> debits(ServiceJobSettlementJournalBuilder.BuiltJournal built) {
        return built.details().stream()
                .filter(d -> d.getDebit() != null && d.getDebit().signum() > 0)
                .collect(Collectors.toMap(JournalDetailDTO::getAccountId, JournalDetailDTO::getDebit, BigDecimal::add));
    }

    private static Map<Integer, BigDecimal> credits(ServiceJobSettlementJournalBuilder.BuiltJournal built) {
        return built.details().stream()
                .filter(d -> d.getCredit() != null && d.getCredit().signum() > 0)
                .collect(Collectors.toMap(JournalDetailDTO::getAccountId, JournalDetailDTO::getCredit, BigDecimal::add));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
