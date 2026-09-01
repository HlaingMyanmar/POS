package org.sspd.servicemgmt.stockoptions.opening.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.opening.dto.OpeningStockRequest;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;

@Service
@RequiredArgsConstructor
public class OpeningStockService {
    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_STOCK_ADJUSTMENT_CREATE','CAN_ACCESS_PRODUCT_CREATE','CAN_ACCESS_PRODUCT_UPDATE')")
    @Transactional
    public void createOpening(OpeningStockRequest req) {
        if (req.getProductId() == null) throw new IllegalArgumentException("Product is required.");
        if (req.getQty() == null || req.getQty() <= 0) throw new IllegalArgumentException("Opening quantity must be greater than zero.");
        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (Boolean.TRUE.equals(product.getHasSerial())) {
            throw new IllegalArgumentException("Opening stock is for quantity products only.");
        }
        int current = product.getStockQty() == null ? 0 : product.getStockQty();
        product.setStockQty(current + req.getQty());
        productRepository.save(product);
        stockMovementService.recordMovement(StockMovement.builder()
                .product(product)
                .movementType(MovementType.IN)
                .qty(req.getQty())
                .referenceType("OpeningStock")
                .referenceId(product.getId())
                .build());
    }
}
