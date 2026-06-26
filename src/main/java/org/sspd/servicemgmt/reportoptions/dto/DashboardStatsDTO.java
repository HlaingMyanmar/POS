package org.sspd.servicemgmt.reportoptions.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardStatsDTO {
    // ── Totals ─────────────────────────────────────────
    private BigDecimal totalSales;
    private BigDecimal totalPurchases;
    private long totalCustomers;
    private long totalServices;

    // ── Today ──────────────────────────────────────────
    private BigDecimal todaySalesAmount;
    private long todaySalesCount;

    // ── AR Alerts ──────────────────────────────────────
    private BigDecimal totalOverdueAR;
    private long overdueARCount;
    private BigDecimal totalPendingAR;
    private long pendingARCount;

    // ── Operations ─────────────────────────────────────
    private long pendingServiceJobs;
    private long lowStockCount;
    private List<String> lowStockProducts;

    // ── System Health ──────────────────────────────────
    private boolean hasJournalEntries;

    // ── Recent Activity ────────────────────────────────
    private List<RecentSaleDTO> recentSales;
}
