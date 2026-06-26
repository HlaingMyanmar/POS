package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.reportoptions.dto.AgingLineItemDTO;
import org.sspd.servicemgmt.reportoptions.dto.AgingReportDTO;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgingReportService {
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ServiceJobRepository serviceJobRepository;

    public static final String CURRENT = "Current";
    public static final String B_0_30 = "0-30";
    public static final String B_31_60 = "31-60";
    public static final String B_61_90 = "61-90";
    public static final String B_OVER_90 = ">90";

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @Transactional(readOnly = true)
    public AgingReportDTO getCustomerAging(LocalDate asOf) {
        List<AgingLineItemDTO> saleLines = saleRepository.findByDueAmountGreaterThan(BigDecimal.ZERO).stream()
                .map(sale -> toCustomerLine(sale, asOf))
                .toList();

        List<AgingLineItemDTO> jobLines = serviceJobRepository.findByDueAmountGreaterThan(BigDecimal.ZERO).stream()
                .map(job -> toServiceJobLine(job, asOf))
                .toList();

        List<AgingLineItemDTO> lines = new ArrayList<>(saleLines);
        lines.addAll(jobLines);
        lines.sort(Comparator.comparingInt(AgingLineItemDTO::getDaysPastDue).reversed()
                .thenComparing(AgingLineItemDTO::getPartyName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return summarize(asOf, lines);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @Transactional(readOnly = true)
    public AgingReportDTO getSupplierAging(LocalDate asOf) {
        List<AgingLineItemDTO> lines = purchaseRepository.findByDueAmountGreaterThan(BigDecimal.ZERO).stream()
                .map(purchase -> toSupplierLine(purchase, asOf))
                .sorted(Comparator.comparingInt(AgingLineItemDTO::getDaysPastDue).reversed()
                        .thenComparing(AgingLineItemDTO::getPartyName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return summarize(asOf, lines);
    }

    private AgingLineItemDTO toCustomerLine(Sale sale, LocalDate asOf) {
        LocalDate invoiceDate = sale.getSaleDate() != null ? sale.getSaleDate().toLocalDate() : asOf;
        LocalDate dueDate = sale.getDueDate() != null ? sale.getDueDate() : invoiceDate.plusDays(30);
        long daysDiff = ChronoUnit.DAYS.between(dueDate, asOf);
        int daysPastDue = (int) Math.max(0, daysDiff);
        int daysToDue = (int) Math.max(0, -daysDiff);
        return AgingLineItemDTO.builder()
                .partyId(sale.getCustomer() != null ? sale.getCustomer().getId() : null)
                .referenceNo(sale.getSaleCode())
                .partyName(sale.getCustomer() != null ? sale.getCustomer().getName() : "Unknown")
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .originalAmount(safe(sale.getNetAmount()))
                .paidAmount(safe(sale.getPaidAmount()))
                .dueAmount(safe(sale.getDueAmount()))
                .daysPastDue(daysPastDue)
                .daysToDue(daysToDue)
                .bucket(resolveBucket(daysDiff))
                .build();
    }

    private AgingLineItemDTO toServiceJobLine(ServiceJob job, LocalDate asOf) {
        LocalDate invoiceDate = job.getCompletedDate() != null ? job.getCompletedDate().toLocalDate() : asOf;
        LocalDate dueDate = job.getDueDate() != null ? job.getDueDate() : invoiceDate.plusDays(30);
        long daysDiff = ChronoUnit.DAYS.between(dueDate, asOf);
        int daysPastDue = (int) Math.max(0, daysDiff);
        int daysToDue = (int) Math.max(0, -daysDiff);
        return AgingLineItemDTO.builder()
                .partyId(job.getCustomer() != null ? job.getCustomer().getId() : null)
                .referenceNo(job.getJobNo())
                .partyName(job.getCustomer() != null ? job.getCustomer().getName() : "Unknown")
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .originalAmount(safe(job.getNetAmount()))
                .paidAmount(safe(job.getPaidAmount()))
                .dueAmount(safe(job.getDueAmount()))
                .daysPastDue(daysPastDue)
                .daysToDue(daysToDue)
                .bucket(resolveBucket(daysDiff))
                .build();
    }

    private AgingLineItemDTO toSupplierLine(Purchase purchase, LocalDate asOf) {
        LocalDate invoiceDate = purchase.getPurchaseDate() != null ? purchase.getPurchaseDate().toLocalDate() : asOf;
        LocalDate dueDate = purchase.getDueDate() != null ? purchase.getDueDate() : invoiceDate.plusDays(30);
        long daysDiff = ChronoUnit.DAYS.between(dueDate, asOf);
        int daysPastDue = (int) Math.max(0, daysDiff);
        int daysToDue = (int) Math.max(0, -daysDiff);
        return AgingLineItemDTO.builder()
                .partyId(purchase.getSupplier() != null ? purchase.getSupplier().getId() : null)
                .referenceNo(purchase.getPurchaseCode())
                .partyName(purchase.getSupplier() != null ? purchase.getSupplier().getName() : "Unknown")
                .invoiceDate(invoiceDate)
                .dueDate(dueDate)
                .originalAmount(safe(purchase.getTotalAmount()))
                .paidAmount(safe(purchase.getPaidAmount()))
                .dueAmount(safe(purchase.getDueAmount()))
                .daysPastDue(daysPastDue)
                .daysToDue(daysToDue)
                .bucket(resolveBucket(daysDiff))
                .build();
    }

    private AgingReportDTO summarize(LocalDate asOf, List<AgingLineItemDTO> lines) {
        BigDecimal bCurrent = sumByBucket(lines, CURRENT);
        BigDecimal b0To30 = sumByBucket(lines, B_0_30);
        BigDecimal b31To60 = sumByBucket(lines, B_31_60);
        BigDecimal b61To90 = sumByBucket(lines, B_61_90);
        BigDecimal bOver90 = sumByBucket(lines, B_OVER_90);
        Set<Integer> partyIds = lines.stream()
                .map(AgingLineItemDTO::getPartyId)
                .collect(Collectors.toSet());
        return AgingReportDTO.builder()
                .asOf(asOf)
                .lines(lines)
                .bucketCurrent(bCurrent)
                .bucket0To30(b0To30)
                .bucket31To60(b31To60)
                .bucket61To90(b61To90)
                .bucketOver90(bOver90)
                .totalOutstanding(bCurrent.add(b0To30).add(b31To60).add(b61To90).add(bOver90))
                .totalInvoices(lines.size())
                .totalParties(partyIds.size())
                .build();
    }

    private BigDecimal sumByBucket(List<AgingLineItemDTO> lines, String bucket) {
        return lines.stream()
                .filter(line -> bucket.equals(line.getBucket()))
                .map(line -> safe(line.getDueAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolveBucket(long daysDiff) {
        if (daysDiff < 0) return CURRENT;
        if (daysDiff <= 30) return B_0_30;
        if (daysDiff <= 60) return B_31_60;
        if (daysDiff <= 90) return B_61_90;
        return B_OVER_90;
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
