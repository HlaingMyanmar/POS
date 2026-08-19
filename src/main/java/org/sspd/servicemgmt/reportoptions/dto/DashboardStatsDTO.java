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
    private BigDecimal periodServiceAmount;
    private long periodServiceCount;
    private BigDecimal periodPurchaseAmount;
    private long periodPurchaseCount;

    // ── AR Alerts ──────────────────────────────────────
    private BigDecimal totalOverdueAR;
    private long overdueARCount;
    private BigDecimal totalPendingAR;
    private long pendingARCount;

    // ── Operations ─────────────────────────────────────
    private long pendingServiceJobs;
    private long receivedJobCount;
    private long inProgressJobCount;
    private long completedJobCount;
    private long pendingPaymentJobCount;
    private long pendingDeliveryJobCount;
    private long lowStockCount;
    private List<String> lowStockProducts;
    private BigDecimal stockValue;
    private BigDecimal supplierPayable;
    private long reworkCount;
    private long upgradeCount;
    private long refundCount;
    private BigDecimal refundAmount;
    private String updatedAt;

    // ── System Health ──────────────────────────────────
    private boolean hasJournalEntries;

    // ── Recent Activity ────────────────────────────────
    private List<RecentSaleDTO> recentSales;
}
