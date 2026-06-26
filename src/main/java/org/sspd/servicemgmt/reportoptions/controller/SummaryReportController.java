package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.repository.ExpenseRepository;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.repository.IncomeRepository;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.repository.SaleDetailRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.repository.StockAdjustmentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SummaryReportController {

    private final SaleRepository saleRepo;
    private final SaleDetailRepository saleDetailRepo;
    private final SaleReturnRepository saleReturnRepo;
    private final PurchaseRepository purchaseRepo;
    private final PurchaseReturnRepository purchaseReturnRepo;
    private final ServiceJobRepository serviceJobRepo;
    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;
    private final StockAdjustmentRepository stockAdjRepo;

    // ── Sales Summary ────────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @GetMapping("/sales-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> salesSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDt = parseDt(from, false);
        LocalDateTime toDt   = parseDt(to, true);

        List<Object[]> totals     = saleRepo.salesTotals(fromDt, toDt);
        List<Object[]> monthly    = saleRepo.monthlySalesSummaryRange(fromDt, toDt);
        List<Object[]> byStaff    = saleRepo.staffSaleStats(
                fromDt != null ? fromDt : LocalDateTime.of(2000, 1, 1, 0, 0),
                toDt   != null ? toDt   : LocalDateTime.now().plusDays(1));
        List<Object[]> byCustomer = saleRepo.salesByCustomer(fromDt, toDt);

        Map<String, Object> result = new LinkedHashMap<>();
        if (!totals.isEmpty()) {
            Object[] r = totals.get(0);
            result.put("totalCount",    ((Number) r[0]).longValue());
            result.put("totalRevenue",  r[1]);
            result.put("totalDiscount", r[2]);
            result.put("totalDue",      r[3]);
        }
        result.put("monthly", monthly.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year",   ((Number) r[0]).intValue());
            m.put("month",  ((Number) r[1]).intValue());
            m.put("label",  YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue()).toString());
            m.put("count",  ((Number) r[2]).longValue());
            m.put("amount", r[3]);
            return m;
        }).toList());
        result.put("byStaff", byStaff.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("staffId",   r[0]);
            m.put("staffName", r[1]);
            m.put("count",     ((Number) r[3]).longValue());
            m.put("amount",    r[4]);
            return m;
        }).toList());
        result.put("topCustomers", byCustomer.stream().limit(10).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerId",   r[0]);
            m.put("customerName", r[1]);
            m.put("count",        ((Number) r[2]).longValue());
            m.put("amount",       r[3]);
            return m;
        }).toList());
        return ResponseEntity.ok(new ApiResponse<>(true, "Sales Summary", result));
    }

    // ── Purchase Summary ─────────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @GetMapping("/purchase-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> purchaseSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDt = parseDt(from, false);
        LocalDateTime toDt   = parseDt(to, true);

        List<Object[]> totals     = purchaseRepo.purchaseTotals(fromDt, toDt);
        List<Object[]> monthly    = purchaseRepo.monthlyPurchaseSummary(fromDt, toDt);
        List<Object[]> bySupplier = purchaseRepo.purchaseBySupplier(fromDt, toDt);

        Map<String, Object> result = new LinkedHashMap<>();
        if (!totals.isEmpty()) {
            Object[] r = totals.get(0);
            result.put("totalCount",  ((Number) r[0]).longValue());
            result.put("totalAmount", r[1]);
            result.put("totalDue",    r[2]);
        }
        result.put("monthly", monthly.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year",   ((Number) r[0]).intValue());
            m.put("month",  ((Number) r[1]).intValue());
            m.put("label",  YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue()).toString());
            m.put("count",  ((Number) r[2]).longValue());
            m.put("amount", r[3]);
            return m;
        }).toList());
        result.put("bySupplier", bySupplier.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("supplierId",   r[0]);
            m.put("supplierName", r[1]);
            m.put("count",        ((Number) r[2]).longValue());
            m.put("amount",       r[3]);
            return m;
        }).toList());
        return ResponseEntity.ok(new ApiResponse<>(true, "Purchase Summary", result));
    }

    // ── Service Job Summary ──────────────────────────────────────────────────
    @PreAuthorize("hasAuthority('CAN_ACCESS_SERVICE_JOB_READ')")
    @GetMapping("/service-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> serviceSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDt = parseDt(from, false);
        LocalDateTime toDt   = parseDt(to, true);

        List<Object[]> byStatus = serviceJobRepo.countByStatusInRange(fromDt, toDt);
        List<Object[]> monthly  = serviceJobRepo.monthlyJobSummary(fromDt, toDt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("byStatus", byStatus.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", r[0]);
            m.put("count",  ((Number) r[1]).longValue());
            return m;
        }).toList());
        result.put("totalJobs", byStatus.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum());
        result.put("monthly", monthly.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("year",   ((Number) r[0]).intValue());
            m.put("month",  ((Number) r[1]).intValue());
            m.put("label",  YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue()).toString());
            m.put("count",  ((Number) r[2]).longValue());
            m.put("amount", r[3]);
            return m;
        }).toList());
        return ResponseEntity.ok(new ApiResponse<>(true, "Service Summary", result));
    }

    // ── Period Summary (Income & Profit) ─────────────────────────────────────
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_REPORT_READ')")
    @GetMapping("/daily-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dailySummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDt = parseDt(from, false);
        LocalDateTime toDt   = parseDt(to, true);

        Map<String, Object> result = buildPeriodSummary(fromDt, toDt);
        return ResponseEntity.ok(new ApiResponse<>(true, "Period Summary", result));
    }

    // ── Yearly Summary ───────────────────────────────────────────────────────
    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_SALE_READ','CAN_ACCESS_SERVICE_JOB_READ','CAN_ACCESS_REPORT_READ')")
    @GetMapping("/yearly-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> yearlySummary(
            @RequestParam(defaultValue = "0") int year) {

        int targetYear = year > 0 ? year : java.time.LocalDate.now().getYear();

        List<Map<String, Object>> months    = new ArrayList<>();
        BigDecimal totalSaleRevenue         = BigDecimal.ZERO;
        BigDecimal totalSaleReturn          = BigDecimal.ZERO;
        BigDecimal totalServiceRevenue      = BigDecimal.ZERO;
        BigDecimal totalOtherIncome         = BigDecimal.ZERO;
        BigDecimal totalPurchaseAmount      = BigDecimal.ZERO;
        BigDecimal totalPurchaseReturn      = BigDecimal.ZERO;
        BigDecimal totalStockAdjLoss        = BigDecimal.ZERO;
        BigDecimal totalExp                 = BigDecimal.ZERO;
        BigDecimal totalSaleProfit          = BigDecimal.ZERO;

        for (int m = 1; m <= 12; m++) {
            LocalDateTime fromDt = LocalDateTime.of(targetYear, m, 1, 0, 0, 0);
            int lastDay = YearMonth.of(targetYear, m).lengthOfMonth();
            LocalDateTime toDt = LocalDateTime.of(targetYear, m, lastDay, 23, 59, 59);

            Map<String, Object> md = buildPeriodSummary(fromDt, toDt);
            md.put("month", m);
            md.put("label", Month.of(m).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            months.add(md);

            totalSaleRevenue    = totalSaleRevenue.add(bd(md.get("saleRevenue")));
            totalSaleReturn     = totalSaleReturn.add(bd(md.get("saleReturnAmount")));
            totalSaleProfit     = totalSaleProfit.add(bd(md.get("saleProfit")));
            totalServiceRevenue = totalServiceRevenue.add(bd(md.get("serviceRevenue")));
            totalOtherIncome    = totalOtherIncome.add(bd(md.get("otherIncome")));
            totalPurchaseAmount = totalPurchaseAmount.add(bd(md.get("purchaseAmount")));
            totalPurchaseReturn = totalPurchaseReturn.add(bd(md.get("purchaseReturnAmount")));
            totalStockAdjLoss   = totalStockAdjLoss.add(bd(md.get("stockAdjLoss")));
            totalExp            = totalExp.add(bd(md.get("totalExpenses")));
        }

        BigDecimal totalNetSaleRevenue  = totalSaleRevenue.subtract(totalSaleReturn);
        BigDecimal totalNetPurchaseCost = totalPurchaseAmount.subtract(totalPurchaseReturn);
        BigDecimal totalIncome          = totalNetSaleRevenue.add(totalServiceRevenue).add(totalOtherIncome);
        BigDecimal totalNetProfit       = totalSaleProfit.subtract(totalSaleReturn)
                                                         .add(totalServiceRevenue)
                                                         .add(totalOtherIncome)
                                                         .subtract(totalStockAdjLoss)
                                                         .subtract(totalExp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year",                      targetYear);
        result.put("months",                    months);
        result.put("totalSaleRevenue",          totalSaleRevenue);
        result.put("totalSaleReturnAmount",     totalSaleReturn);
        result.put("totalNetSaleRevenue",       totalNetSaleRevenue);
        result.put("totalServiceRevenue",       totalServiceRevenue);
        result.put("totalOtherIncome",          totalOtherIncome);
        result.put("totalIncome",               totalIncome);
        result.put("totalPurchaseAmount",       totalPurchaseAmount);
        result.put("totalPurchaseReturnAmount", totalPurchaseReturn);
        result.put("totalNetPurchaseCost",      totalNetPurchaseCost);
        result.put("totalStockAdjLoss",         totalStockAdjLoss);
        result.put("totalExpenses",             totalExp);
        result.put("totalNetProfit",            totalNetProfit);
        return ResponseEntity.ok(new ApiResponse<>(true, "Yearly Summary", result));
    }

    // ── Shared period builder ─────────────────────────────────────────────────

    private Map<String, Object> buildPeriodSummary(LocalDateTime fromDt, LocalDateTime toDt) {
        List<Object[]> saleTotals = saleRepo.salesTotals(fromDt, toDt);
        BigDecimal saleRevenue = BigDecimal.ZERO;
        long saleCount = 0;
        if (!saleTotals.isEmpty()) {
            Object[] r = saleTotals.get(0);
            saleCount   = ((Number) r[0]).longValue();
            saleRevenue = r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO;
        }

        BigDecimal saleProfit = coalesce(saleDetailRepo.saleProfitInRange(fromDt, toDt));

        BigDecimal saleReturnAmount = coalesce(saleReturnRepo.sumInRange(fromDt, toDt));

        BigDecimal serviceRevenue = coalesce(serviceJobRepo.sumNetAmountInRange(fromDt, toDt));

        BigDecimal otherIncome = coalesce(incomeRepo.sumInRange(fromDt, toDt));

        List<Object[]> purchaseTotals = purchaseRepo.purchaseTotals(fromDt, toDt);
        BigDecimal purchaseAmount = BigDecimal.ZERO;
        if (!purchaseTotals.isEmpty() && purchaseTotals.get(0)[1] != null)
            purchaseAmount = (BigDecimal) purchaseTotals.get(0)[1];

        BigDecimal purchaseReturnAmount = coalesce(purchaseReturnRepo.sumInRange(fromDt, toDt));

        BigDecimal stockAdjLoss = coalesce(stockAdjRepo.sumLossValueInRange(fromDt, toDt));

        BigDecimal totalExpenses = coalesce(expenseRepo.sumInRange(fromDt, toDt));

        BigDecimal netSaleRevenue  = saleRevenue.subtract(saleReturnAmount);
        BigDecimal netPurchaseCost = purchaseAmount.subtract(purchaseReturnAmount);
        BigDecimal totalIncome     = netSaleRevenue.add(serviceRevenue).add(otherIncome);
        BigDecimal grossProfit     = saleProfit.add(serviceRevenue).add(otherIncome);
        BigDecimal netProfit       = grossProfit.subtract(saleReturnAmount)
                                                .subtract(stockAdjLoss)
                                                .subtract(totalExpenses);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("saleCount",            saleCount);
        r.put("saleRevenue",          saleRevenue);
        r.put("saleReturnAmount",     saleReturnAmount);
        r.put("netSaleRevenue",       netSaleRevenue);
        r.put("saleProfit",           saleProfit);
        r.put("serviceRevenue",       serviceRevenue);
        r.put("otherIncome",          otherIncome);
        r.put("totalIncome",          totalIncome);
        r.put("purchaseAmount",       purchaseAmount);
        r.put("purchaseReturnAmount", purchaseReturnAmount);
        r.put("netPurchaseCost",      netPurchaseCost);
        r.put("stockAdjLoss",         stockAdjLoss);
        r.put("totalExpenses",        totalExpenses);
        r.put("grossProfit",          grossProfit);
        r.put("netProfit",            netProfit);
        return r;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal coalesce(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private BigDecimal bd(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private LocalDateTime parseDt(String s, boolean endOfDay) {
        if (s == null || s.isBlank()) return null;
        return endOfDay
                ? LocalDateTime.parse(s + "T23:59:59")
                : LocalDateTime.parse(s + "T00:00:00");
    }
}
