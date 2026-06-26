package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;
import org.sspd.servicemgmt.reportoptions.dto.ProfitLossDTO;
import org.sspd.servicemgmt.reportoptions.dto.ProfitLossLineItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfitLossService {

    private final JournalDetailRepository journalDetailRepository;

    // Income accounts ထဲမှ P&L မှ ခွဲထုတ်သော codes (Revenue section မှာ တွက်ထားပြီး)
    private static final List<String> REVENUE_INCOME_CODES = List.of(
            AccountCode.SALES,        // INC-002
            AccountCode.PURCHASE_RTN  // INC-007
    );

    // Expense accounts ထဲမှ P&L မှ ခွဲထုတ်သော codes (Purchases section မှာ တွက်ထားပြီး)
    private static final List<String> PURCHASES_EXPENSE_CODES = List.of(
            AccountCode.PURCHASES,   // EXP-007
            AccountCode.SALES_RTN,   // EXP-010
            AccountCode.COGS         // EXP-006 (perpetual COGS — ဒီ system မှာ မသုံးသောကြောင့် ဖယ်ထား)
    );

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @Transactional(readOnly = true)
    public ProfitLossDTO getProfitLoss(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt   = to.atTime(23, 59, 59);

        // ── Section 1: Revenue ───────────────────────────────────────────────
        BigDecimal grossSales    = credit(AccountCode.SALES,       fromDt, toDt);
        BigDecimal salesReturns  = debit(AccountCode.SALES_RTN,    fromDt, toDt);
        BigDecimal netRevenue    = grossSales.subtract(salesReturns);

        // ── Section 2: Purchases ─────────────────────────────────────────────
        BigDecimal purchases       = debit(AccountCode.PURCHASES,    fromDt, toDt);
        BigDecimal purchaseReturns = credit(AccountCode.PURCHASE_RTN, fromDt, toDt);
        BigDecimal netPurchases    = purchases.subtract(purchaseReturns);

        // ── Section 3: Gross Profit ──────────────────────────────────────────
        BigDecimal grossProfit = netRevenue.subtract(netPurchases);

        // ── Section 4: Other Income ──────────────────────────────────────────
        // INC-002 (Sales) နဲ့ INC-007 (Purchase Returns) ကို ဖယ်ပြီး ကျန်သော Income accounts
        List<ProfitLossLineItem> otherIncomeItems = toLineItems(
                journalDetailRepository.sumCreditNetExclude(
                        AccountType.Income, REVENUE_INCOME_CODES, fromDt, toDt));
        BigDecimal totalOtherIncome = sum(otherIncomeItems);

        // ── Section 5: Operating Expenses ────────────────────────────────────
        // EXP-007 (Purchases), EXP-010 (Sales Returns), EXP-006 (COGS) ကို ဖယ်ပြီး ကျန်သော Expense accounts
        List<ProfitLossLineItem> expenseItems = toLineItems(
                journalDetailRepository.sumDebitNetExclude(
                        AccountType.Expense, PURCHASES_EXPENSE_CODES, fromDt, toDt));
        BigDecimal totalExpenses = sum(expenseItems);

        // ── Net Profit ────────────────────────────────────────────────────────
        BigDecimal netProfit = grossProfit.add(totalOtherIncome).subtract(totalExpenses);

        return ProfitLossDTO.builder()
                .from(from)
                .to(to)
                .grossSales(grossSales)
                .salesReturns(salesReturns)
                .netRevenue(netRevenue)
                .purchases(purchases)
                .purchaseReturns(purchaseReturns)
                .netPurchases(netPurchases)
                .grossProfit(grossProfit)
                .otherIncomeItems(otherIncomeItems)
                .totalOtherIncome(totalOtherIncome)
                .expenseItems(expenseItems)
                .totalExpenses(totalExpenses)
                .netProfit(netProfit)
                .build();
    }

    private BigDecimal credit(String code, LocalDateTime from, LocalDateTime to) {
        BigDecimal val = journalDetailRepository.netCreditByCode(code, from, to);
        return val != null ? val.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    private BigDecimal debit(String code, LocalDateTime from, LocalDateTime to) {
        BigDecimal val = journalDetailRepository.netDebitByCode(code, from, to);
        return val != null ? val.max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    private List<ProfitLossLineItem> toLineItems(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new ProfitLossLineItem(
                        (String) row[0],
                        (String) row[1],
                        ((BigDecimal) row[2]).abs()))
                .filter(item -> item.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private BigDecimal sum(List<ProfitLossLineItem> items) {
        return items.stream()
                .map(ProfitLossLineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
