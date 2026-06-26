package org.sspd.servicemgmt.reportoptions.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitLossDTO {
    private LocalDate from;
    private LocalDate to;

    // ── Section 1: Revenue ──────────────────────────
    private BigDecimal grossSales;       // Sales (INC-002)
    private BigDecimal salesReturns;     // Sales Returns (EXP-010)
    private BigDecimal netRevenue;       // grossSales - salesReturns

    // ── Section 2: Cost of Goods ────────────────────
    private BigDecimal purchases;        // Purchases (EXP-007)
    private BigDecimal purchaseReturns;  // Purchase Returns (INC-007)
    private BigDecimal netPurchases;     // purchases - purchaseReturns

    // ── Section 3: Gross Profit ─────────────────────
    private BigDecimal grossProfit;      // netRevenue - netPurchases

    // ── Section 4: Other Income ─────────────────────
    private List<ProfitLossLineItem> otherIncomeItems;
    private BigDecimal totalOtherIncome;

    // ── Section 5: Operating Expenses ──────────────
    private List<ProfitLossLineItem> expenseItems;
    private BigDecimal totalExpenses;

    // ── Bottom Line ─────────────────────────────────
    private BigDecimal netProfit;        // grossProfit + totalOtherIncome - totalExpenses
}
