package org.sspd.servicemgmt.stockoptions.productoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class InventoryAutoAlertService {
    private final ProductRepository products;
    private final SimpMessagingTemplate messaging;
    private final Set<Integer> activeAlerts = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelay = 300000)
    public void check() {
        var low = products.findReorderNeeded();
        var current = low.stream().map(p -> p.getId()).collect(java.util.stream.Collectors.toSet());
        activeAlerts.retainAll(current);
        for (var product : low) {
            if (!activeAlerts.add(product.getId())) continue;
            var payload = new LinkedHashMap<String, Object>();
            payload.put("productId", product.getId());
            payload.put("productCode", product.getProductCode());
            payload.put("productName", product.getName());
            payload.put("stockQty", product.getStockQty() == null ? 0 : product.getStockQty());
            payload.put("reorderLevel", product.getReorderLevel() == null ? 0 : product.getReorderLevel());
            payload.put("alertedAt", LocalDateTime.now());
            messaging.convertAndSend("/topic/inventory-alert", payload);
        }
    }
}
