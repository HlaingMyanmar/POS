package org.sspd.servicemgmt.servicejoboptions.support;

import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the balanced settlement journal for a service job:
 * DR payment / AR / labor discount / parts discount
 * CR service revenue / product sales
 *
 * Revenue credits use balances <em>before</em> overall discount (after line discounts).
 * Overall discount shares are posted to Labor/Parts Discount accounts.
 * Inventory COGS is intentionally separate (via internal service-job sale).
 */
public final class ServiceJobSettlementJournalBuilder {

    private ServiceJobSettlementJournalBuilder() {}

    public record PaymentSlice(Integer accountId, BigDecimal amount) {}

    public record AccountIds(
            Integer serviceRevenueId,
            Integer productSalesId,
            Integer laborDiscountId,
            Integer partsDiscountId,
            Integer receivableId
    ) {}

    public record BuiltJournal(List<JournalDetailDTO> details, BigDecimal totalDebit, BigDecimal totalCredit) {
        public boolean isBalanced() {
            return totalDebit.compareTo(totalCredit) == 0;
        }
    }

    public static BuiltJournal build(
            ServiceJobSettlementBreakdown breakdown,
            List<PaymentSlice> payments,
            BigDecimal paid,
            BigDecimal due,
            AccountIds accounts
    ) {
        Objects.requireNonNull(breakdown, "breakdown");
        Objects.requireNonNull(accounts, "accounts");

        BigDecimal laborBalance = nz(breakdown.laborBalance());
        BigDecimal partsBalance = nz(breakdown.partsBalance());
        BigDecimal laborNet = nz(breakdown.laborNet());
        BigDecimal partsNet = nz(breakdown.partsNet());

        // Fallback settle path: no line/part balances, nets carry the amount.
        if (laborBalance.add(partsBalance).signum() <= 0) {
            laborBalance = laborNet;
            partsBalance = partsNet;
        }

        BigDecimal laborDiscount = laborBalance.subtract(laborNet).max(BigDecimal.ZERO);
        BigDecimal partsDiscount = partsBalance.subtract(partsNet).max(BigDecimal.ZERO);

        List<JournalDetailDTO> details = new ArrayList<>();

        BigDecimal cash = nz(paid);
        if (cash.signum() > 0) {
            if (payments != null && !payments.isEmpty()) {
                for (PaymentSlice slice : payments) {
                    if (slice == null || slice.accountId() == null || nz(slice.amount()).signum() <= 0) continue;
                    details.add(debit(slice.accountId(), slice.amount()));
                }
            }
        }

        BigDecimal ar = nz(due);
        if (ar.signum() > 0) {
            details.add(debit(accounts.receivableId(), ar));
        }
        if (laborDiscount.signum() > 0) {
            details.add(debit(accounts.laborDiscountId(), laborDiscount));
        }
        if (partsDiscount.signum() > 0) {
            details.add(debit(accounts.partsDiscountId(), partsDiscount));
        }
        if (laborBalance.signum() > 0) {
            details.add(credit(accounts.serviceRevenueId(), laborBalance));
        }
        if (partsBalance.signum() > 0) {
            details.add(credit(accounts.productSalesId(), partsBalance));
        }

        BigDecimal totalDebit = sumDebit(details);
        BigDecimal totalCredit = sumCredit(details);
        return new BuiltJournal(details, totalDebit, totalCredit);
    }

    /** Separate perpetual inventory cost recognition (cost price, never selling price). */
    public static BuiltJournal buildCogs(Integer cogsAccountId, Integer inventoryAccountId, BigDecimal totalCost) {
        BigDecimal cost = nz(totalCost);
        if (cost.signum() <= 0) {
            return new BuiltJournal(List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<JournalDetailDTO> details = List.of(
                debit(cogsAccountId, cost),
                credit(inventoryAccountId, cost)
        );
        return new BuiltJournal(details, cost, cost);
    }

    public static String settlementReference(String jobNo) {
        return jobNo + "-SETTLE-" + System.currentTimeMillis();
    }

    public static String settlementReferencePrefix(String jobNo) {
        return jobNo + "-SETTLE";
    }

    private static JournalDetailDTO debit(Integer accountId, BigDecimal amount) {
        JournalDetailDTO d = new JournalDetailDTO();
        d.setAccountId(accountId);
        d.setDebit(amount);
        d.setCredit(BigDecimal.ZERO);
        return d;
    }

    private static JournalDetailDTO credit(Integer accountId, BigDecimal amount) {
        JournalDetailDTO d = new JournalDetailDTO();
        d.setAccountId(accountId);
        d.setDebit(BigDecimal.ZERO);
        d.setCredit(amount);
        return d;
    }

    private static BigDecimal sumDebit(List<JournalDetailDTO> details) {
        return details.stream()
                .map(d -> d.getDebit() != null ? d.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumCredit(List<JournalDetailDTO> details) {
        return details.stream()
                .map(d -> d.getCredit() != null ? d.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
