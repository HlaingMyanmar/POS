package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.reportoptions.dto.DashboardStatsDTO;
import org.sspd.servicemgmt.reportoptions.dto.RecentSaleDTO;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final CustomerRepository customerRepository;
    private final ServiceJobRepository serviceJobRepository;
    private final ProductRepository productRepository;
    private final JournalDetailRepository journalDetailRepository;

    private static final int LOW_STOCK_THRESHOLD = 5;

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // ── Totals ─────────────────────────────────────
        BigDecimal totalSales     = safe(saleRepository.sumTotalNetAmount());
        BigDecimal totalPurchases = safe(purchaseRepository.sumTotalAmount());
        long totalCustomers       = customerRepository.count();
        long totalServices        = saleRepository.count();

        // ── Today ──────────────────────────────────────
        BigDecimal todaySalesAmount = safe(saleRepository.sumSalesFrom(todayStart));
        long todaySalesCount        = saleRepository.countSalesFrom(todayStart);

        // ── AR Alerts ──────────────────────────────────
        BigDecimal totalOverdueAR = safe(saleRepository.sumOverdueAR());
        long overdueARCount       = saleRepository.countOverdueAR();
        BigDecimal totalPendingAR = safe(saleRepository.sumAllPendingAR());
        long pendingARCount       = saleRepository.countAllPendingAR();

        // ── Operations ─────────────────────────────────
        long pendingServiceJobs = serviceJobRepository.countByStatus(ServiceJobStatus.RECEIVED)
                + serviceJobRepository.countByStatus(ServiceJobStatus.INSPECTING)
                + serviceJobRepository.countByStatus(ServiceJobStatus.IN_PROGRESS);

        long lowStockCount          = productRepository.countLowStock(LOW_STOCK_THRESHOLD);
        List<String> lowStockNames  = productRepository.findLowStockNames(LOW_STOCK_THRESHOLD)
                .stream().limit(5).toList();

        // ── System Health ──────────────────────────────
        boolean hasJournalEntries = journalDetailRepository.count() > 0;

        // ── Recent Sales ───────────────────────────────
        List<RecentSaleDTO> recentSales = saleRepository.findTop10ByOrderByIdDesc()
                .stream()
                .map(s -> new RecentSaleDTO(
                        s.getId(),
                        s.getSaleCode(),
                        s.getCustomer().getName(),
                        s.getNetAmount(),
                        s.getSaleDate() != null ? s.getSaleDate().toLocalDate().toString() : "",
                        s.getPaymentStatus() != null ? s.getPaymentStatus().name() : "Pending"
                ))
                .toList();

        return DashboardStatsDTO.builder()
                .totalSales(totalSales)
                .totalPurchases(totalPurchases)
                .totalCustomers(totalCustomers)
                .totalServices(totalServices)
                .todaySalesAmount(todaySalesAmount)
                .todaySalesCount(todaySalesCount)
                .totalOverdueAR(totalOverdueAR)
                .overdueARCount(overdueARCount)
                .totalPendingAR(totalPendingAR)
                .pendingARCount(pendingARCount)
                .pendingServiceJobs(pendingServiceJobs)
                .lowStockCount(lowStockCount)
                .lowStockProducts(lowStockNames)
                .hasJournalEntries(hasJournalEntries)
                .recentSales(recentSales)
                .build();
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
