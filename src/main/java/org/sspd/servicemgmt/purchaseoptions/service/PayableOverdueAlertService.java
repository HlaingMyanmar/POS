package org.sspd.servicemgmt.purchaseoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ✅ Overdue payable reminders — supplier vouchers past due date with balance.
 * Broadcasts a summary over WebSocket every morning at 08:00 and hourly re-checks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayableOverdueAlertService {

    private final PurchaseRepository purchaseRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Rangoon")
    public void morningAlert() {
        broadcast();
    }

    // Hourly safety net — catches overdue vouchers created during the day
    @Scheduled(fixedRate = 3_600_000, initialDelay = 600_000)
    public void recurringCheck() {
        broadcast();
    }

    private void broadcast() {
        try {
            var overdue = purchaseRepository.findOverduePayables(LocalDate.now());
            if (overdue.isEmpty()) return;

            BigDecimal totalOverdue = overdue.stream()
                    .map(p -> p.getDueAmount() != null ? p.getDueAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "PURCHASE_OVERDUE");
            payload.put("count", overdue.size());
            payload.put("totalOverdueAmount", totalOverdue);
            payload.put("oldestDueDate", overdue.get(overdue.size() - 1).getDueDate());
            payload.put("items", overdue.stream().map(p -> Map.of(
                    "purchaseId", p.getId(),
                    "purchaseCode", p.getPurchaseCode() != null ? p.getPurchaseCode() : String.valueOf(p.getId()),
                    "supplierName", p.getSupplier() != null ? p.getSupplier().getName() : "-",
                    "dueDate", String.valueOf(p.getDueDate()),
                    "dueAmount", p.getDueAmount() != null ? p.getDueAmount() : BigDecimal.ZERO
            )).toList());

            messagingTemplate.convertAndSend("/topic/purchase-overdue", payload);
        } catch (Exception e) {
            log.warn("Overdue payable alert failed: {}", e.getMessage());
        }
    }
}
