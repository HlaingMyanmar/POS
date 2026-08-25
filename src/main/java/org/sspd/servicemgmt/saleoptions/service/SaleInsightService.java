package org.sspd.servicemgmt.saleoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.creditoptions.repository.CustomerCreditApplicationRepository;
import org.sspd.servicemgmt.creditoptions.repository.CustomerPaymentAllocationRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.saleoptions.dto.SaleTimelineEventDTO;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.repository.StockMovementRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SaleInsightService {
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CustomerPaymentAllocationRepository allocationRepository;
    private final CustomerCreditApplicationRepository creditApplicationRepository;
    private final StockMovementRepository stockMovementRepository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public List<SaleTimelineEventDTO> timeline(Integer saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + saleId));
        List<SaleTimelineEventDTO> events = new ArrayList<>();
        String code = sale.getSaleCode() == null ? "#" + sale.getId() : sale.getSaleCode();
        LocalDateTime createdAt = sale.getSaleDate() != null ? sale.getSaleDate() : LocalDateTime.now();
        events.add(event("CREATED", createdAt, "Sale confirmed",
                sale.getCustomer() != null ? sale.getCustomer().getName() : "", code, nvl(sale.getNetAmount())));
        if (Boolean.TRUE.equals(sale.getVoided())) {
            events.add(event("VOIDED", sale.getVoidedAt() != null ? sale.getVoidedAt() : createdAt,
                    "Sale voided", sale.getVoidReason(), code, nvl(sale.getNetAmount())));
        }
        paymentTransactionRepository.findByReferenceIdAndReferenceType(saleId, ReferenceType.Sale).forEach(tx -> {
            if (Boolean.TRUE.equals(tx.getReversed())) {
                events.add(event("PAYMENT_REVERSED", tx.getReversedAt() != null ? tx.getReversedAt() : tx.getPaymentDate(),
                        "Payment reversed", tx.getReversalReason(), tx.getTransactionNo(), nvl(tx.getAmount())));
            } else {
                String method = tx.getPaymentMethod() != null ? tx.getPaymentMethod().getMethodName() : "Payment";
                events.add(event("PAYMENT", tx.getPaymentDate(), "Payment received", method, tx.getTransactionNo(), nvl(tx.getAmount())));
            }
        });
        allocationRepository.findBySaleId(saleId).forEach(a -> events.add(event("CUSTOMER_PAYMENT",
                a.getCustomerPayment() != null ? a.getCustomerPayment().getPaymentDate() : createdAt,
                "Customer payment allocated",
                a.getCustomerPayment() != null ? a.getCustomerPayment().getPaymentNo() : "",
                a.getCustomerPayment() != null ? a.getCustomerPayment().getPaymentNo() : null,
                nvl(a.getAmount()))));
        creditApplicationRepository.findBySaleIdOrderByIdDesc(saleId).forEach(c -> events.add(event("CREDIT",
                c.getAppliedAt(), "Customer credit applied", c.getReason(), c.getApplicationNo(), nvl(c.getAmount()))));
        saleReturnRepository.findAllBySaleIdAndDeletedFalse(saleId).forEach(r -> events.add(event("RETURN",
                r.getReturnDate(), "Sale return", r.getReason(), r.getReturnCode(), nvl(r.getTotalReturnAmount()))));
        stockMovementRepository.findByReferenceIdAndReferenceType(saleId, "Sale").forEach(m -> events.add(event("STOCK",
                m.getCreatedAt(), "Stock " + (m.getMovementType() == null ? "OUT" : m.getMovementType().name()),
                m.getProduct() != null ? m.getProduct().getName() : "", code,
                BigDecimal.valueOf(m.getQty() == null ? 0 : m.getQty()))));
        events.sort(Comparator.comparing(SaleTimelineEventDTO::getAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return events;
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_SALE_READ')")
    @Transactional(readOnly = true)
    public Map<String, Object> stats(String dateFrom, String dateTo) {
        LocalDateTime from = parseStart(dateFrom);
        LocalDateTime to = parseEnd(dateTo);
        List<Object[]> totals = saleRepository.salesTotals(from, to);
        Object[] row = totals.isEmpty() ? new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO} : totals.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", row[0]);
        result.put("netAmount", row[1]);
        result.put("discountAmount", row[2]);
        result.put("dueAmount", row[3]);
        result.put("returnAmount", saleReturnRepository.sumInRange(from, to));
        return result;
    }

    private SaleTimelineEventDTO event(String type, LocalDateTime at, String title, String detail, String refCode, BigDecimal amount) {
        return SaleTimelineEventDTO.builder().type(type).at(at).title(title).detail(detail).refCode(refCode).amount(amount).build();
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private LocalDateTime parseStart(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay();
    }

    private LocalDateTime parseEnd(String s) {
        if (s == null || s.isBlank()) return null;
        return java.time.LocalDate.parse(s).atStartOfDay().plusDays(1);
    }
}
