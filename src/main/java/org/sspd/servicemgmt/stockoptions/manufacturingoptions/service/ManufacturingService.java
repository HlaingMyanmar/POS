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
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

import java.math.BigDecimal;
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

    public List<ManufacturingOrderDTO> getAll() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

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
                .finishedProductSellingPrice(dto.getFinishedProductSellingPrice() != null ? dto.getFinishedProductSellingPrice() : BigDecimal.ZERO)
                .notes(dto.getNotes())
                .build();

        order = orderRepository.save(order);
        order.setOrderCode("MFG-" + String.format("%06d", order.getId()));

        if (dto.getItems() != null) {
            for (ManufacturingOrderItemDTO itemDto : dto.getItems()) {
                ManufacturingOrderItem item = buildItem(order, itemDto);
                order.getItems().add(item);
            }
        }

        order = orderRepository.save(order);
        return toDTO(order);
    }

    @Transactional
    public ManufacturingOrderDTO update(Integer id, ManufacturingOrderDTO dto) {
        ManufacturingOrder order = findOrder(id);
        if (order.getStatus() != ManufacturingStatus.DRAFT) {
            throw new IllegalStateException("DRAFT အဆင့်မှသာ ပြင်ဆင်နိုင်သည်");
        }

        order.setFinishedProductName(dto.getFinishedProductName());
        order.setFinishedProductBrandId(dto.getFinishedProductBrandId());
        order.setFinishedProductCategoryId(dto.getFinishedProductCategoryId());
        order.setFinishedProductUnitId(dto.getFinishedProductUnitId());
        order.setFinishedProductType(dto.getFinishedProductType() != null ? dto.getFinishedProductType() : "New");
        order.setFinishedProductSellingPrice(dto.getFinishedProductSellingPrice() != null ? dto.getFinishedProductSellingPrice() : BigDecimal.ZERO);
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
            throw new IllegalStateException("DRAFT အဆင့်မှသာ ပြီးဆုံးနိုင်သည်");
        }
        if (order.getItems().isEmpty()) {
            throw new IllegalStateException("ပစ္စည်းများ မထည့်ရသေးပါ");
        }
        if (order.getFinishedProductCategoryId() == null || order.getFinishedProductBrandId() == null || order.getFinishedProductUnitId() == null) {
            throw new IllegalStateException("ထုတ်ကုန်၏ အမျိုးအစား၊ ဘရန်း၊ ယူနစ် ဖြည့်ရန်လိုသည်");
        }

        BigDecimal totalCost = BigDecimal.ZERO;

        for (ManufacturingOrderItem item : order.getItems()) {
            Product component = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProductId()));

            if (Boolean.FALSE.equals(component.getHasSerial())) {
                int needed = item.getQty() != null ? item.getQty() : 1;
                int available = component.getStockQty() != null ? component.getStockQty() : 0;
                if (available < needed) {
                    throw new IllegalStateException(component.getName() + " လက်ကျန် မလုံလောက်ပါ (လိုသည်: " + needed + ", ရှိသည်: " + available + ")");
                }
                component.setStockQty(available - needed);
                productRepository.save(component);
            } else {
                List<Integer> serialIds = item.getSelectedSerialIds();
                if (serialIds == null || serialIds.isEmpty()) {
                    throw new IllegalStateException(component.getName() + " အတွက် Serial နံပါတ်ရွေးရန်လိုသည်");
                }
                for (Integer serialId : serialIds) {
                    ProductSerial serial = serialRepository.findById(serialId)
                            .orElseThrow(() -> new ResourceNotFoundException("Serial not found: " + serialId));
                    if (serial.getStatus() != SerialStatus.Available) {
                        throw new IllegalStateException("Serial " + serial.getSerialNumber() + " ရရှိနိုင်ခြင်းမရှိပါ");
                    }
                    serial.setStatus(SerialStatus.Sold);
                    serialRepository.save(serial);
                }
            }

            BigDecimal itemCost = (item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(item.getQty() != null ? item.getQty() : 1));
            totalCost = totalCost.add(itemCost);
        }

        Product finished = Product.builder()
                .productCode("PENDING")
                .name(order.getFinishedProductName())
                .productType(org.sspd.servicemgmt.stockoptions.productoptions.enums.ProductType.valueOf(order.getFinishedProductType()))
                .sellingPrice(order.getFinishedProductSellingPrice())
                .costPrice(totalCost)
                .hasSerial(false)
                .stockQty(1)
                .reorderLevel(0)
                .warrantyMonths(0)
                .build();

        if (order.getFinishedProductCategoryId() != null) {
            categoryRepository.findById(order.getFinishedProductCategoryId().longValue())
                    .ifPresent(finished::setCategory);
        }
        if (order.getFinishedProductBrandId() != null) {
            brandRepository.findById(order.getFinishedProductBrandId().longValue())
                    .ifPresent(finished::setBrand);
        }
        if (order.getFinishedProductUnitId() != null) {
            unitRepository.findById(order.getFinishedProductUnitId().longValue())
                    .ifPresent(finished::setUnit);
        }

        finished = productRepository.save(finished);
        finished.setProductCode("PRD-" + String.format("%06d", finished.getId()));
        productRepository.save(finished);

        order.setStatus(ManufacturingStatus.COMPLETED);
        order.setFinishedProductId(finished.getId());
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);

        return toDTO(order);
    }

    @Transactional
    public ManufacturingOrderDTO cancel(Integer id) {
        ManufacturingOrder order = findOrder(id);
        if (order.getStatus() == ManufacturingStatus.COMPLETED) {
            throw new IllegalStateException("ပြီးဆုံးသော order ကို ပယ်ဖျက်မရပါ");
        }
        order.setStatus(ManufacturingStatus.CANCELLED);
        return toDTO(orderRepository.save(order));
    }

    @Transactional
    public void delete(Integer id) {
        ManufacturingOrder order = findOrder(id);
        if (order.getStatus() == ManufacturingStatus.COMPLETED) {
            throw new IllegalStateException("ပြီးဆုံးသော order ကို ဖျက်မရပါ");
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

        ManufacturingOrderItem item = ManufacturingOrderItem.builder()
                .order(order)
                .productId(dto.getProductId())
                .productName(product.getName())
                .qty(dto.getQty() != null ? dto.getQty() : 1)
                .unitCost(dto.getUnitCost() != null ? dto.getUnitCost() : (product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO))
                .selectedSerialIds(dto.getSelectedSerialIds() != null ? new ArrayList<>(dto.getSelectedSerialIds()) : new ArrayList<>())
                .build();
        return item;
    }

    private ManufacturingOrderDTO toDTO(ManufacturingOrder order) {
        BigDecimal totalCost = BigDecimal.ZERO;
        List<ManufacturingOrderItemDTO> itemDTOs = new ArrayList<>();

        for (ManufacturingOrderItem item : order.getItems()) {
            BigDecimal lineCost = (item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(item.getQty() != null ? item.getQty() : 1));
            totalCost = totalCost.add(lineCost);

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
                    available = (int) serialRepository.findAll().stream()
                            .filter(s -> s.getProduct().getId().equals(item.getProductId()) && s.getStatus() == SerialStatus.Available)
                            .count();
                }
            }

            itemDTOs.add(ManufacturingOrderItemDTO.builder()
                    .id(item.getId())
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .productCode(product != null ? product.getProductCode() : null)
                    .hasSerial(product != null ? product.getHasSerial() : null)
                    .qty(item.getQty())
                    .unitCost(item.getUnitCost())
                    .selectedSerialIds(item.getSelectedSerialIds())
                    .selectedSerialNumbers(serialNumbers)
                    .availableQty(available)
                    .build());
        }

        String brandName = null;
        if (order.getFinishedProductBrandId() != null) {
            brandName = brandRepository.findById(order.getFinishedProductBrandId().longValue())
                    .map(b -> b.getName()).orElse(null);
        }
        String categoryName = null;
        if (order.getFinishedProductCategoryId() != null) {
            categoryName = categoryRepository.findById(order.getFinishedProductCategoryId().longValue())
                    .map(c -> c.getName()).orElse(null);
        }
        String unitName = null;
        if (order.getFinishedProductUnitId() != null) {
            unitName = unitRepository.findById(order.getFinishedProductUnitId().longValue())
                    .map(u -> u.getUnitName()).orElse(null);
        }

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
                .finishedProductId(order.getFinishedProductId())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .completedAt(order.getCompletedAt() != null ? order.getCompletedAt().toString() : null)
                .items(itemDTOs)
                .totalComponentCost(totalCost)
                .build();
    }
}
