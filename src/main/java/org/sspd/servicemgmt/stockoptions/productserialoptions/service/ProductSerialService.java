package org.sspd.servicemgmt.stockoptions.productserialoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.categoryoptions.model.Category;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetailWarranty;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.repository.PurchaseDetailWarrantyRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.dto.ProductSerialDTO;
import org.sspd.servicemgmt.stockoptions.productserialoptions.mapper.ProductSerialMapper;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.service.WarehouseResolver;

import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ProductSerialService {
    private final SimpMessagingTemplate messagingTemplate;
    private static final String PRODUCT_SERIAL_TOPIC = "/topic/productSerial";
    private final ProductSerialRepository productSerialRepository;
    private final ProductSerialMapper mapper;
    private final ProductRepository productRepository;
    private final PurchaseDetailWarrantyRepository warrantyRepository;
    private final WarehouseResolver warehouseResolver;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_CREATE')")
    @Transactional
    public ProductSerialDTO save(ProductSerialDTO dto) {
        if (productSerialRepository.existsBySerialNumber(dto.getSerialNumber())) {
            throw new RuntimeException(
                    "Product Serial '" + dto.getSerialNumber() + "' is already registered!"
            );
        }
        ProductSerial entity = mapper.toEntity(dto);
        applyNotNullDefaults(entity);
        applyWarrantyFields(entity, dto);

        if (dto.getProductId() != null) {
            org.sspd.servicemgmt.stockoptions.productoptions.model.Product product = productRepository
                    .findById(dto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            entity.setProduct(product);
        } else {
            throw new RuntimeException("Product ID is required for Product Serial");
        }
        entity.setWarehouse(warehouseResolver.require(dto.getWarehouseId(), dto.getWarehouseName()));

        ProductSerial savedEntity = productSerialRepository.save(entity);
        messagingTemplate.convertAndSend(PRODUCT_SERIAL_TOPIC, "PRODUCT_SERIAL_CREATED");
        return enrichPurchaseInfo(mapper.toDto(savedEntity));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @Transactional(readOnly = true)
    public List<ProductSerialDTO> findAll(){
        return productSerialRepository.findAll()
                .stream()
                .map(mapper::toDto)
                .map(this::enrichPurchaseInfo)
                .toList();
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @Transactional(readOnly = true)
    public List<ProductSerialDTO> findByProductId(Integer productId) {
        return productSerialRepository.findByProductId(productId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @Transactional(readOnly = true)
    public ProductSerialDTO findBySerialNumber(String serialNumber) {
        ProductSerial entity = productSerialRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Product Serial Not Found: " + serialNumber));
        return enrichPurchaseInfo(mapper.toDto(entity));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_READ')")
    @Transactional(readOnly = true)
    public ProductSerialDTO findById(Integer id){
       ProductSerial entity = productSerialRepository.findById(id)
               .orElseThrow(()->new ResourceNotFoundException("Product Serial Not Found with id " + id));
       return enrichPurchaseInfo(mapper.toDto(entity));

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_UPDATE')")
    @Transactional
    public ProductSerialDTO update(Integer id, ProductSerialDTO dto) {

        ProductSerial existingEntity = productSerialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Serial Not Found with id " + id));
        if (!existingEntity.getSerialNumber().equals(dto.getSerialNumber())
                && productSerialRepository.existsBySerialNumber(dto.getSerialNumber())) {
            throw new RuntimeException("Product Serial '" + dto.getSerialNumber() + "' is already registered!");
        }
        mapper.updateEntityFromDto(dto, existingEntity);
        applyNotNullDefaults(existingEntity);
        applyWarrantyFields(existingEntity, dto);
        if (dto.getProductId() != null &&
                (existingEntity.getProduct() == null || !existingEntity.getProduct().getId().equals(dto.getProductId()))) {
            org.sspd.servicemgmt.stockoptions.productoptions.model.Product product = productRepository
                    .findById(dto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            existingEntity.setProduct(product);
        }
        if (dto.getWarehouseId() != null || (dto.getWarehouseName() != null && !dto.getWarehouseName().isBlank())) {
            existingEntity.setWarehouse(warehouseResolver.require(dto.getWarehouseId(), dto.getWarehouseName()));
        }
        ProductSerial savedEntity = productSerialRepository.save(existingEntity);
        messagingTemplate.convertAndSend(PRODUCT_SERIAL_TOPIC, "PRODUCT_SERIAL_UPDATED");

        return enrichPurchaseInfo(mapper.toDto(savedEntity));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_UPDATE')")
    @Transactional
    public void updatePhoto(Integer id, String photoBase64) {
        if (id == null) throw new ResourceNotFoundException("Product Serial id must not be null");
        ProductSerial entity = productSerialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Serial Not Found with id " + id));
        entity.setPhotoBase64(photoBase64);
        productSerialRepository.save(entity);
        messagingTemplate.convertAndSend(PRODUCT_SERIAL_TOPIC, "PRODUCT_SERIAL_UPDATED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_DELETE')")
    @Transactional
    public void delete(Integer id){
        ProductSerial existingEntity = productSerialRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Product Serial Not Found with id " + id));
        productSerialRepository.delete(existingEntity);
        messagingTemplate.convertAndSend(PRODUCT_SERIAL_TOPIC,"PRODUCT_SERIAL_DELETE");
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_SERIAL_DELETE')")
    @Transactional
    public void deleteBySerialNumber(String serialNumber) {
        if (!productSerialRepository.existsBySerialNumber(serialNumber)) {
            throw new ResourceNotFoundException("Product Serial Not Found with serial number: " + serialNumber);
        }

        productSerialRepository.deleteBySerialNumber(serialNumber);
        messagingTemplate.convertAndSend(PRODUCT_SERIAL_TOPIC, "PRODUCT_SERIAL_DELETE");
    }



    @Transactional(readOnly = true)
    public List<ProductSerialDTO> findBySerialNumbers(List<String> serials) {
        if (serials == null || serials.isEmpty()) return List.of();
        return productSerialRepository.findBySerialNumberIn(serials)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    private void applyNotNullDefaults(ProductSerial entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus.Available);
        }
    }

    private void applyWarrantyFields(ProductSerial entity, ProductSerialDTO dto) {
        Integer months = dto.getWarrantyMonths() != null ? dto.getWarrantyMonths() : entity.getWarrantyMonths();
        LocalDate start = dto.getWarrantyStartDate() != null ? dto.getWarrantyStartDate() : entity.getWarrantyStartDate();
        LocalDate explicitEnd = dto.getWarrantyEndDate();

        if (months != null && months < 0) {
            throw new RuntimeException("Warranty months cannot be negative");
        }
        if (months != null && months > 0 && start == null) {
            start = LocalDate.now();
        }

        entity.setWarrantyMonths(months);
        entity.setWarrantyStartDate(start);
        if (explicitEnd != null) {
            entity.setWarrantyEndDate(explicitEnd);
        } else if (months != null && months > 0 && start != null) {
            entity.setWarrantyEndDate(start.plusMonths(months));
        } else {
            entity.setWarrantyEndDate(null);
        }
    }

    private ProductSerialDTO enrichPurchaseInfo(ProductSerialDTO dto) {
        if (dto.getSerialNumber() == null || dto.getSerialNumber().isBlank()) return dto;
        warrantyRepository.findTopBySerialNumberOrderByIdDesc(dto.getSerialNumber()).ifPresent(w -> {
            if (w.getPurchaseDetail() != null && w.getPurchaseDetail().getPurchase() != null) {
                var purchase = w.getPurchaseDetail().getPurchase();
                dto.setPurchaseId(purchase.getId());
                dto.setPurchaseCode(purchase.getPurchaseCode());
                dto.setPurchaseDate(purchase.getPurchaseDate());
                if (purchase.getSupplier() != null) {
                    dto.setSupplierName(purchase.getSupplier().getName());
                }
            }
        });
        return dto;
    }
}
