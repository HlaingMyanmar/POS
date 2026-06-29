package org.sspd.servicemgmt.stockoptions.manufacturingoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.dto.ManufacturingOrderDTO;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.dto.ManufacturingOrderItemDTO;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.enums.ManufacturingStatus;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.model.ManufacturingOrder;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.model.ManufacturingOrderItem;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.repository.ManufacturingOrderRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManufacturingService {

    private final ManufacturingOrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductSerialRepository serialRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final StockMovementService stockMovementService;

    @Transactional(readOnly = true)
    public List<ManufacturingOrderDTO> getAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ManufacturingOrderDTO getById(Integer id) {
        return toDTO(findOrder(id));
    }

    @Transactional
    public ManufacturingOrderDTO create(ManufacturingOrderDTO dto) {
        ManufacturingOrder order = ManufacturingOrder.builder()
                .orderCode("PENDING")
                .finishedProductName(dto.getFinishedProductName())
                .finishedProductBrandId(dto.getFinishedProductBrandId())
                .finishedProductCategoryId(dto.getFinishedProductCategoryId())
                .finishedProductUnitId(dto.getFinishedProductUnitId())
                .finishedProductType(dto.getFinishedProductType() != null ? dto.getFinishedProductType() : "New")
                .finishedProductSellingPrice(nonNullMoney(dto.getFinishedProductSellingPrice()))
                .productionQty(normalizeQty(dto.getProductionQty()))
                .laborCost(nonNullMoney(dto.getLaborCost()))
                .overheadCost(nonNullMoney(dto.getOverheadCost()))
                .wasteCost(nonNullMoney(dto.getWasteCost()))
                .finishedProductId(dto.getFinishedProductId())
                .notes(dto.getNotes())
                .build();

        applyExistingFinishedProduct(order);

        order = orderRepository.save(order);
        order.setOrderCode("MFG-" + String.format("%06d", order.getId()));

        if (dto.getItems() != null) {
            for (ManufacturingOrderItemDTO itemDto : dto.getItems()) {
                order.getItems().add(buildItem(order, itemDto));
            }
        }

        order = orderRepository.save(order);
        return toDTO(order);
    }

    @Transactional
    public ManufacturingOrderDTO update(Integer id, ManufacturingOrderDTO dto) {
        ManufacturingOrder order = findOrder(id);
        if (order.getStatus() != ManufacturingStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT manufacturing orders can be edited");
        }

        order.setFinishedProductName(dto.getFinishedProductName());
        order.setFinishedProductBrandId(dto.getFinishedProductBrandId());
        order.setFinishedProductCategoryId(dto.getFinishedProductCategoryId());
        order.setFinishedProductUnitId(dto.getFinishedProductUnitId());
        order.setFinishedProductType(dto.getFinishedProductType() != null ? dto.getFinishedProductType() : "New");
        order.setFinishedProductSellingPrice(nonNullMoney(dto.getFinishedProductSellingPrice()));
        order.setProductionQty(normalizeQty(dto.getProductionQty()));
        order.setLaborCost(nonNullMoney(dto.getLaborCost()));
        order.setOverheadCost(nonNullMoney(dto.getOverheadCost()));
        order.setWasteCost(nonNullMoney(dto.getWasteCost()));
        order.setFinishedProductId(dto.getFinishedProductId());
        applyExistingFinishedProduct(order);
        order.setNotes(dto.getNotes());

        order.getItems().clear();
        if (dto.getItems() != null) {
            for (ManufacturingOrderItemDTO itemDto : dto.getItems()) {
                order.getItems().add(buildItem(order, itemDto));
            }
        }

        return toDTO(orderRepository.save(order));
    }

    @Transactional
    public ManufacturingOrderDTO complete(Integer id) {
        ManufacturingOrder order = findOrder(id);
        if (order.getStatus() != ManufacturingStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT manufacturing orders can be completed");
        }
        if (order.getItems().isEmpty()) {
            throw new IllegalStateException("Component items are required");
        }
        if (order.getFinishedProductId() == null && (order.getFinishedProductCategoryId() == null || order.getFinishedProductBrandId() == null || order.getFinishedProductUnitId() == null)) {
            throw new IllegalStateException("Finished product category, brand, and unit are required");
        }

        BigDecimal totalComponentCost = BigDecimal.ZERO;

        for (ManufacturingOrderItem item : order.getItems()) {
            Product component = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProductId()));

            if (Boolean.FALSE.equals(component.getHasSerial())) {
                int needed = itemQty(item);
                int available = component.getStockQty() != null ? component.getStockQty() : 0;
                if (available < needed) {
                    throw new IllegalStateException(component.getName() + " stock is not enough (needed: " + needed + ", available: " + available + ")");
                }
                component.setStockQty(available - needed);
                productRepository.save(component);
            } else {
                List<Integer> serialIds = item.getSelectedSerialIds();
                if (serialIds == null || serialIds.size() != itemQty(item)) {
                    throw new IllegalStateException(component.getName() + " requires exactly " + itemQty(item) + " serial numbers");
                }
                for (Integer serialId : serialIds) {
                    ProductSerial serial = serialRepository.findById(serialId)
                            .orElseThrow(() -> new ResourceNotFoundException("Serial not found: " + serialId));
                    if (serial.getStatus() != SerialStatus.Available) {
                        throw new IllegalStateException("Serial " + serial.getSerialNumber() + " is not available");
                    }
                    serial.setStatus(SerialStatus.Consumed_In_Manufacturing);
                    serialRepository.save(serial);
                }

            }

            totalComponentCost = totalComponentCost.add(lineCost(item));
            stockMovementService.recordMovement(StockMovement.builder()
                    .product(component)
                    .movementType(MovementType.OUT)
                    .qty(itemQty(item))
                    .referenceType("Manufacturing")
                    .referenceId(order.getId())
                    .build());
        }

        int productionQty = normalizeQty(order.getProductionQty());
        BigDecimal totalProductionCost = totalComponentCost
                .add(nonNullMoney(order.getLaborCost()))
                .add(nonNullMoney(order.getOverheadCost()))
                .add(nonNullMoney(order.getWasteCost()));
        BigDecimal unitProductionCost = totalProductionCost.divide(BigDecimal.valueOf(productionQty), 2, RoundingMode.HALF_UP);

        Product finished = resolveFinishedProduct(order, productionQty, totalProductionCost, unitProductionCost);

        order.setStatus(ManufacturingStatus.COMPLETED);
        order.setFinishedProductId(finished.getId());
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);

        stockMovementService.recordMovement(StockMovement.builder()
                .product(finished)
                .movementType(MovementType.IN)
                .qty(productionQty)
                .referenceType("Manufacturing")
                .referenceId(order.getId())
                .build());

        return toDTO(order);
    }

    private void applyExistingFinishedProduct(ManufacturingOrder order) {
        if (order.getFinishedProductId() == null) return;
        Product product = productRepository.findById(order.getFinishedProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Finished product not found: " + order.getFinishedProductId()));
        order.setFinishedProductName(product.getName());
        order.setFinishedProductType(product.getProductType() != null ? product.getProductType().name() : "New");
        order.setFinishedProductSellingPrice(nonNullMoney(product.getSellingPrice()));
        order.setFinishedProductBrandId(product.getBrand() != null ? product.getBrand().getId() : null);
        order.setFinishedProductCategoryId(product.getCategory() != null ? product.getCategory().getId() : null);
        order.setFinishedProductUnitId(product.getUnit() != null ? product.getUnit().getId() : null);
    }

    private Product resolveFinishedProduct(ManufacturingOrder order, int productionQty, BigDecimal totalProductionCost, BigDecimal unitProductionCost) {
        if (order.getFinishedProductId() != null) {
            Product finished = productRepository.findById(order.getFinishedProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Finished product not found: " + order.getFinishedProductId()));
            if (Boolean.TRUE.equals(finished.getHasSerial())) {
                throw new IllegalStateException("Manufactured finished product must be non-serial: " + finished.getName());
            }
            int currentQty = finished.getStockQty() != null ? finished.getStockQty() : 0;
            BigDecimal currentCost = finished.getCostPrice() != null ? finished.getCostPrice() : BigDecimal.ZERO;
            BigDecimal currentValue = currentCost.multiply(BigDecimal.valueOf(Math.max(0, currentQty)));
            BigDecimal averageCost = currentValue.add(totalProductionCost)
                    .divide(BigDecimal.valueOf(Math.max(1, currentQty + productionQty)), 2, RoundingMode.HALF_UP);
            finished.setStockQty(currentQty + productionQty);
            finished.setCostPrice(averageCost);
            if (order.getFinishedProductSellingPrice() != null && order.getFinishedProductSellingPrice().compareTo(BigDecimal.ZERO) > 0) {
                finished.setSellingPrice(order.getFinishedProductSellingPrice());
            }
            return productRepository.save(finished);
        }

        productRepository.findByName(order.getFinishedProductName()).ifPresent(existing -> {
            throw new IllegalStateException("Product already exists. Select existing finished product instead: " + existing.getName());
        });

        Product finished = Product.builder()
                .productCode("PENDING")
                .name(order.getFinishedProductName())
                .productType(org.sspd.servicemgmt.stockoptions.productoptions.enums.ProductType.valueOf(order.getFinishedProductType()))
                .sellingPrice(order.getFinishedProductSellingPrice())
                .costPrice(unitProductionCost)
                .hasSerial(false)
                .stockQty(productionQty)
                .reorderLevel(0)
                .warrantyMonths(0)
                .build();

        if (order.getFinishedProductCategoryId() != null) {
            categoryRepository.findById(order.getFinishedProductCategoryId().longValue()).ifPresent(finished::setCategory);
        }
        if (order.getFinishedProductBrandId() != null) {
            brandRepository.findById(order.getFinishedProductBrandId().longValue()).ifPresent(finished::setBrand);
        }
        if (order.getFinishedProductUnitId() != null) {
            unitRepository.findById(order.getFinishedProductUnitId().longValue()).ifPresent(finished::setUnit);
        }

        finished = productRepository.save(finished);
        finished.setProductCode("PRD-" + String.format("%06d", finished.getId()));
        return productRepository.save(finished);
    }

    @Transactional
    public ManufacturingOrderDTO cancel(Integer id) {
        ManufacturingOrder order = findOrder(id);
        if (order.getStatus() == ManufacturingStatus.COMPLETED) {
            throw new IllegalStateException("Completed manufacturing orders cannot be cancelled");
        }
        order.setStatus(ManufacturingStatus.CANCELLED);
        return toDTO(orderRepository.save(order));
    }

    @Transactional
    public void delete(Integer id) {
        ManufacturingOrder order = findOrder(id);
        if (order.getStatus() == ManufacturingStatus.COMPLETED) {
            throw new IllegalStateException("Completed manufacturing orders cannot be deleted");
        }
        orderRepository.delete(order);
    }

    private ManufacturingOrder findOrder(Integer id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manufacturing order not found: " + id));
    }

    private ManufacturingOrderItem buildItem(ManufacturingOrder order, ManufacturingOrderItemDTO dto) {
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + dto.getProductId()));

        return ManufacturingOrderItem.builder()
                .order(order)
                .productId(dto.getProductId())
                .productName(product.getName())
                .qty(normalizeQty(dto.getQty()))
                .unitCost(dto.getUnitCost() != null ? dto.getUnitCost() : nonNullMoney(product.getCostPrice()))
                .selectedSerialIds(dto.getSelectedSerialIds() != null ? new ArrayList<>(dto.getSelectedSerialIds()) : new ArrayList<>())
                .build();
    }

    private ManufacturingOrderDTO toDTO(ManufacturingOrder order) {
        BigDecimal totalComponentCost = BigDecimal.ZERO;
        List<ManufacturingOrderItemDTO> itemDTOs = new ArrayList<>();

        for (ManufacturingOrderItem item : order.getItems()) {
            totalComponentCost = totalComponentCost.add(lineCost(item));

            List<String> serialNumbers = new ArrayList<>();
            if (item.getSelectedSerialIds() != null && !item.getSelectedSerialIds().isEmpty()) {
                for (Integer sid : item.getSelectedSerialIds()) {
                    serialRepository.findById(sid).ifPresent(s -> serialNumbers.add(s.getSerialNumber()));
                }
            }

            Product product = productRepository.findById(item.getProductId()).orElse(null);
            int available = 0;
            if (product != null) {
                if (Boolean.FALSE.equals(product.getHasSerial())) {
                    available = product.getStockQty() != null ? product.getStockQty() : 0;
                } else {
                    available = serialRepository.countByProductIdAndStatus(item.getProductId(), SerialStatus.Available).intValue();
                }
            }

            itemDTOs.add(ManufacturingOrderItemDTO.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .productCode(product != null ? product.getProductCode() : null)
                    .hasSerial(product != null ? product.getHasSerial() : null)
                    .qty(itemQty(item))
                    .unitCost(item.getUnitCost())
                    .selectedSerialIds(item.getSelectedSerialIds())
                    .selectedSerialNumbers(serialNumbers)
                    .availableQty(available)
                    .build());
        }

        String brandName = order.getFinishedProductBrandId() != null
                ? brandRepository.findById(order.getFinishedProductBrandId().longValue()).map(b -> b.getName()).orElse(null) : null;
        String categoryName = order.getFinishedProductCategoryId() != null
                ? categoryRepository.findById(order.getFinishedProductCategoryId().longValue()).map(c -> c.getName()).orElse(null) : null;
        String unitName = order.getFinishedProductUnitId() != null
                ? unitRepository.findById(order.getFinishedProductUnitId().longValue()).map(u -> u.getUnitName()).orElse(null) : null;

        BigDecimal totalProductionCost = totalComponentCost
                .add(nonNullMoney(order.getLaborCost()))
                .add(nonNullMoney(order.getOverheadCost()))
                .add(nonNullMoney(order.getWasteCost()));
        BigDecimal unitProductionCost = totalProductionCost.divide(BigDecimal.valueOf(normalizeQty(order.getProductionQty())), 2, RoundingMode.HALF_UP);

        return ManufacturingOrderDTO.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus().name())
                .finishedProductName(order.getFinishedProductName())
                .finishedProductBrandId(order.getFinishedProductBrandId())
                .finishedProductBrandName(brandName)
                .finishedProductCategoryId(order.getFinishedProductCategoryId())
                .finishedProductCategoryName(categoryName)
                .finishedProductUnitId(order.getFinishedProductUnitId())
                .finishedProductUnitName(unitName)
                .finishedProductType(order.getFinishedProductType())
                .finishedProductSellingPrice(order.getFinishedProductSellingPrice())
                .productionQty(normalizeQty(order.getProductionQty()))
                .laborCost(nonNullMoney(order.getLaborCost()))
                .overheadCost(nonNullMoney(order.getOverheadCost()))
                .wasteCost(nonNullMoney(order.getWasteCost()))
                .finishedProductId(order.getFinishedProductId())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .completedAt(order.getCompletedAt() != null ? order.getCompletedAt().toString() : null)
                .items(itemDTOs)
                .totalComponentCost(totalComponentCost)
                .totalProductionCost(totalProductionCost)
                .unitProductionCost(unitProductionCost)
                .build();
    }

    private int normalizeQty(Integer qty) {
        return qty != null && qty > 0 ? qty : 1;
    }

    private int itemQty(ManufacturingOrderItem item) {
        return normalizeQty(item.getQty());
    }

    private BigDecimal nonNullMoney(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private BigDecimal lineCost(ManufacturingOrderItem item) {
        return nonNullMoney(item.getUnitCost()).multiply(BigDecimal.valueOf(itemQty(item)));
    }
}