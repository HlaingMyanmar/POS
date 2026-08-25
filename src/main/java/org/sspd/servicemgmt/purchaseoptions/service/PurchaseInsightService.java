package org.sspd.servicemgmt.purchaseoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseAnalyticsDTO;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseTimelineEventDTO;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository.GoodsReceiptRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.repository.PurchaseDetailRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierCreditApplicationRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierPaymentAllocationRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.repository.StockMovementRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseInsightService {
    private final PurchaseRepository purchaseRepository;
    private final PurchaseDetailRepository purchaseDetailRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SupplierPaymentAllocationRepository allocationRepository;
    private final SupplierCreditApplicationRepository creditApplicationRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final StockMovementRepository stockMovementRepository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public List<PurchaseTimelineEventDTO> timeline(Integer purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + purchaseId));
        List<PurchaseTimelineEventDTO> events = new ArrayList<>();
        String code = purchase.getPurchaseCode() == null ? "#" + purchase.getId() : purchase.getPurchaseCode();
        LocalDateTime createdAt = purchase.getPurchaseDate() != null ? purchase.getPurchaseDate() : LocalDateTime.now();
        boolean draft = purchase.isDraft();
        events.add(event(draft ? "DRAFT" : "CREATED", createdAt, draft ? "Draft saved" : "Purchase confirmed",
                purchase.getSupplier() != null ? purchase.getSupplier().getName() : "", code, nvl(purchase.getNetAmount())));
        if (purchase.isCancelled()) {
            events.add(event("CANCELLED", purchase.getCancelledAt() != null ? purchase.getCancelledAt() : createdAt,
                    "Purchase cancelled", purchase.getCancelReason(), code, nvl(purchase.getNetAmount())));
        }
        paymentTransactionRepository.findByReferenceIdAndReferenceType(purchaseId, ReferenceType.Purchase).forEach(tx -> {
            if (Boolean.TRUE.equals(tx.getReversed())) {
                events.add(event("PAYMENT_REVERSED", tx.getReversedAt() != null ? tx.getReversedAt() : tx.getPaymentDate(),
                        "Payment reversed", tx.getReversalReason(), tx.getTransactionNo(), nvl(tx.getAmount())));
            } else {
                String method = tx.getPaymentMethod() != null ? tx.getPaymentMethod().getMethodName() : "Payment";
                events.add(event("PAYMENT", tx.getPaymentDate(), "Payment received", method, tx.getTransactionNo(), nvl(tx.getAmount())));
            }
        });
        allocationRepository.findByPurchaseId(purchaseId).forEach(a -> events.add(event("SUPPLIER_PAYMENT",
                a.getSupplierPayment() != null ? a.getSupplierPayment().getPaymentDate() : createdAt,
                "Supplier payment allocated",
                a.getSupplierPayment() != null ? a.getSupplierPayment().getPaymentNo() : "",
                a.getSupplierPayment() != null ? a.getSupplierPayment().getPaymentNo() : null,
                nvl(a.getAmount()))));
        creditApplicationRepository.findByTargetPurchaseIdOrderByIdDesc(purchaseId).forEach(c -> events.add(event("CREDIT",
                c.getAppliedAt(), "Supplier credit applied", c.getReason(), c.getApplicationNo(), nvl(c.getAmount()))));
        purchaseReturnRepository.findByPurchaseId(purchaseId).forEach(r -> events.add(event("RETURN",
                r.getReturnDate(), "Purchase return", r.getReason(), r.getReturnNo(), nvl(r.getTotalReturnAmount()))));
        goodsReceiptRepository.findByPurchaseIdOrderByIdDesc(purchaseId).forEach(g -> events.add(event("GRN",
                g.getReceivedAt(), "Goods received", g.getMatchStatus(), g.getGrnCode(), null)));
        stockMovementRepository.findByReferenceIdAndReferenceType(purchaseId, "Purchase").forEach(m -> events.add(event("STOCK",
                m.getCreatedAt(), "Stock " + (m.getMovementType() == null ? "IN" : m.getMovementType().name()),
                m.getProduct() != null ? m.getProduct().getName() : "", code, BigDecimal.valueOf(m.getQty() == null ? 0 : m.getQty()))));
        events.sort(Comparator.comparing(PurchaseTimelineEventDTO::getAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return events;
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_PURCHASE_ANALYTICS','CAN_ACCESS_REPORT_READ')")
    @Transactional(readOnly = true)
    public PurchaseAnalyticsDTO analytics(String dateFrom, String dateTo) {
        LocalDateTime from = parseStart(dateFrom);
        LocalDateTime to = parseEnd(dateTo);
        Object[] totals = first(purchaseRepository.analyticsTotals(from, to));
        PurchaseAnalyticsDTO dto = PurchaseAnalyticsDTO.builder()
                .voucherCount(numLong(totals, 0))
                .totalSpent(numDec(totals, 1))
                .paidAmount(numDec(totals, 2))
                .dueAmount(numDec(totals, 3))
                .taxAmount(numDec(totals, 4))
                .withholdingTaxAmount(numDec(totals, 5))
                .landedCostAmount(numDec(totals, 6))
                .returnAmount(numDec(totals, 7))
                .foreignAmount(numDec(totals, 8))
                .fxVoucherCount(numLong(totals, 9))
                .build();
        dto.setByCategory(purchaseDetailRepository.spendByCategory(from, to).stream()
                .map(row -> named(str(row[0], "Uncategorized"), numLong(row, 1), numDec(row, 2))).toList());
        dto.setBySupplier(purchaseRepository.purchaseBySupplier(from, toExclusive(to)).stream()
                .map(row -> named(str(row[1], "Supplier"), numLong(row, 2), numDec(row, 3))).toList());
        dto.setByCurrency(purchaseRepository.spendByCurrency(from, to).stream()
                .map(row -> named(str(row[0], "MMK"), numLong(row, 1), numDec(row, 2))).toList());
        Object[] grn = first(goodsReceiptRepository.matchCounts(from, to));
        dto.setGrnCount(numLong(grn, 0));
        dto.setGrnVarianceCount(numLong(grn, 1));
        return dto;
    }

    private PurchaseTimelineEventDTO event(String type, LocalDateTime at, String title, String detail, String refCode, BigDecimal amount) {
        return PurchaseTimelineEventDTO.builder().type(type).at(at).title(title).detail(detail).refCode(refCode).amount(amount).build();
    }

    private PurchaseAnalyticsDTO.NamedAmount named(String name, long count, BigDecimal amount) {
        return PurchaseAnalyticsDTO.NamedAmount.builder().name(name).count(count).amount(amount).build();
    }

    private Object[] first(List<Object[]> rows) {
        return rows == null || rows.isEmpty() || rows.get(0) == null ? new Object[10] : rows.get(0);
    }

    private long numLong(Object[] row, int i) {
        if (row == null || i >= row.length || row[i] == null) return 0L;
        return ((Number) row[i]).longValue();
    }

    private BigDecimal numDec(Object[] row, int i) {
        if (row == null || i >= row.length || row[i] == null) return BigDecimal.ZERO;
        return row[i] instanceof BigDecimal b ? b : new BigDecimal(row[i].toString());
    }

    private String str(Object v, String fallback) {
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private LocalDateTime parseStart(String value) {
        if (value == null || value.isBlank()) return null;
        return java.time.LocalDate.parse(value).atStartOfDay();
    }

    private LocalDateTime parseEnd(String value) {
        if (value == null || value.isBlank()) return null;
        return java.time.LocalDate.parse(value).plusDays(1).atStartOfDay();
    }

    private LocalDateTime toExclusive(LocalDateTime to) {
        return to;
    }
}
