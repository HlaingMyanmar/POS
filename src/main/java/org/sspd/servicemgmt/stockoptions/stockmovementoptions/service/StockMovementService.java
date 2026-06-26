package org.sspd.servicemgmt.stockoptions.stockmovementoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.dto.StockMovementDTO;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.mapper.StockMovementMapper;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.repository.StockMovementRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository repository;
    private final StockMovementMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String STOCK_MOVEMENT_TOPIC = "/topic/stock-movement";

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @Transactional(readOnly = true)
    public List<StockMovementDTO> findAll() {
        return repository.findAllWithProduct().stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @Transactional(readOnly = true)
    public List<StockMovementDTO> findByProduct(Integer productId) {
        return repository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @Transactional(readOnly = true)
    public List<StockMovementDTO> findByProductAndDateRange(Integer productId, java.time.LocalDateTime from, java.time.LocalDateTime to) {
        return repository.findByProductIdAndCreatedAtBetweenOrderByCreatedAtDesc(productId, from, to).stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_READ')")
    @Transactional(readOnly = true)
    public List<StockMovementDTO> findByMovementType(org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType type) {
        return repository.findByMovementTypeOrderByCreatedAtDesc(type).stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * Internal use from purchase/sale/service modules. No external permission check here;
     * caller is responsible for authorization.
     */
    @Transactional
    public void recordMovement(StockMovement movement) {
        if (movement == null) {
            throw new RuntimeException("Stock movement is required");
        }
        if (movement.getProduct() == null) {
            throw new RuntimeException("Product is required for stock movement");
        }
        if (movement.getQty() == null || movement.getQty() <= 0) {
            throw new RuntimeException("Qty must be greater than zero");
        }
        repository.save(movement);
        messagingTemplate.convertAndSend(STOCK_MOVEMENT_TOPIC, "STOCK_UPDATED");
    }
}
