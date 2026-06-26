package org.sspd.servicemgmt.stockoptions.productoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.brandoptions.model.Brand;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.categoryoptions.model.Category;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ProductDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.mapper.ProductMapper;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.unitsoptions.model.Unit;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final SimpMessagingTemplate messagingTemplate;
    private static final String PRODUCT_TOPIC = "/topic/product";
    private final ProductRepository productRepository;
    private final ProductMapper mapper;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitRepository unitRepository;
    private final ProductSerialRepository productSerialRepository;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_CREATE')")
    @Transactional
    public ProductDTO save(ProductDTO dto) {

        Product entity = mapper.toEntity(dto);
        entity.setProductCode("PENDING"); // temporary to satisfy not-null before ID-based code
        entity.setReorderLevel(sanitizeReorderLevel(dto.getReorderLevel()));

        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findById(dto.getCategoryId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            entity.setCategory(category);
        }

        if (dto.getBrandId() != null) {
            Brand brand = brandRepository.findById(dto.getBrandId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            entity.setBrand(brand);
        }

        if (dto.getUnitId() != null) {
            Unit unit = unitRepository.findById(dto.getUnitId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
            entity.setUnit(unit);
        }

        // STEP 1: Save first to get auto increment ID
        Product savedEntity = productRepository.save(entity);

        // STEP 2: Generate Sequential Prefix Code
        String generatedCode = "PRD-" + String.format("%06d", savedEntity.getId());
        savedEntity.setProductCode(generatedCode);

        // STEP 3: Save again with productCode
        productRepository.save(savedEntity);

        messagingTemplate.convertAndSend(PRODUCT_TOPIC, "PRODUCT_CREATED");

        return mapper.toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @Transactional(readOnly = true)
    public List<ProductDTO> findAll(){
        return productRepository.findAll()
                .stream()
                .map(this::toDtoWithAvailability)
                .toList();

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @Transactional(readOnly = true)
    public ProductDTO findById(Integer id){
        Product entity = productRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Product Not Found with id " + id));
        return toDtoWithAvailability(entity);

    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @Transactional
    public ProductDTO update(Integer id, ProductDTO dto) {

        Product existingEntity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found with id " + id));

        mapper.updateEntityFromDto(dto, existingEntity);
        if (dto.getReorderLevel() != null) {
            existingEntity.setReorderLevel(sanitizeReorderLevel(dto.getReorderLevel()));
        }

        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findById(dto.getCategoryId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            existingEntity.setCategory(category);
        }

        if (dto.getBrandId() != null) {
            Brand brand = brandRepository.findById(dto.getBrandId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
            existingEntity.setBrand(brand);
        }

        if (dto.getUnitId() != null) {
            Unit unit = unitRepository.findById(dto.getUnitId().longValue())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit not found"));
            existingEntity.setUnit(unit);
        }

        Product savedEntity = productRepository.save(existingEntity);

        messagingTemplate.convertAndSend(PRODUCT_TOPIC, "PRODUCT_UPDATE");

        return toDtoWithAvailability(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_DELETE')")
    @Transactional
    public void delete(Integer id){
        Product existingEntity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found with id " + id));
        productRepository.delete(existingEntity);
        messagingTemplate.convertAndSend(PRODUCT_TOPIC, "PRODUCT_DELETE");

    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @Transactional(readOnly = true)
    public List<ProductDTO> findLowStock() {
        return productRepository.findAll().stream()
                .map(this::toDtoWithAvailability)
                .filter(dto -> {
                    int stock = dto.getStockQty() != null ? dto.getStockQty() : 0;
                    int reorder = dto.getReorderLevel() != null ? dto.getReorderLevel() : 0;
                    return reorder > 0 && stock <= reorder;
                })
                .toList();
    }


    private ProductDTO toDtoWithAvailability(Product entity) {
        ProductDTO dto = mapper.toDto(entity);
        if (Boolean.TRUE.equals(entity.getHasSerial())) {
            Long count = productSerialRepository.countByProductIdAndStatus(entity.getId(), SerialStatus.Available);
            int available = count != null ? count.intValue() : 0;
            dto.setAvailableSerialCount(available);
            dto.setStockQty(available);
            int rawQty = entity.getStockQty() != null ? entity.getStockQty() : 0;
            dto.setUnlinkedQty(rawQty > 0 ? rawQty : 0);
        } else {
            dto.setAvailableSerialCount(null);
            dto.setStockQty(entity.getStockQty());
            dto.setUnlinkedQty(0);
        }
        dto.setHasSerial(entity.getHasSerial());
        int reorderLevel = sanitizeReorderLevel(entity.getReorderLevel());
        dto.setReorderLevel(reorderLevel);
        int stock = dto.getStockQty() != null ? dto.getStockQty() : 0;
        dto.setShortageQty(Math.max(0, reorderLevel - stock));
        return dto;
    }

    /**
     * Retroactively assign serial numbers to a qty-only product.
     * Converts the product to serial-tracked and creates ProductSerial records.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @Transactional
    public ProductDTO assignSerials(Integer productId, List<String> serialNumbers, Integer warrantyMonths) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (Boolean.TRUE.equals(product.getHasSerial())) {
            throw new RuntimeException("Product is already serial-tracked. Add serials via Serial Management instead.");
        }

        int currentQty = product.getStockQty() != null ? product.getStockQty() : 0;
        if (currentQty <= 0) {
            throw new RuntimeException("Product has no stock to assign serials to.");
        }
        if (serialNumbers == null || serialNumbers.size() != currentQty) {
            throw new RuntimeException("Serial count (" + (serialNumbers == null ? 0 : serialNumbers.size())
                    + ") must match current stock qty (" + currentQty + ").");
        }

        int months = warrantyMonths != null && warrantyMonths > 0 ? warrantyMonths : 0;
        LocalDate today = LocalDate.now();

        for (String sn : serialNumbers) {
            if (sn == null || sn.isBlank()) throw new RuntimeException("Serial number cannot be blank.");
            if (productSerialRepository.existsBySerialNumber(sn.trim()))
                throw new RuntimeException("Serial '" + sn.trim() + "' already exists.");
            productSerialRepository.save(
                    org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial.builder()
                            .product(product)
                            .serialNumber(sn.trim())
                            .status(org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus.Available)
                            .warrantyMonths(months)
                            .warrantyStartDate(months > 0 ? today : null)
                            .warrantyEndDate(months > 0 ? today.plusMonths(months) : null)
                            .build());
        }

        product.setHasSerial(true);
        product.setStockQty(0);
        messagingTemplate.convertAndSend(PRODUCT_TOPIC, "PRODUCT_UPDATE");
        return toDtoWithAvailability(productRepository.save(product));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @Transactional(readOnly = true)
    public int getNextSerialSeq(Integer productId) {
        long count = productSerialRepository.countByProductId(productId);
        return (int) count + 1;
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @Transactional
    public void updatePhoto(Integer id, String photoBase64) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found with id " + id));
        product.setPhotoBase64(photoBase64);
        productRepository.save(product);
        messagingTemplate.convertAndSend(PRODUCT_TOPIC, "PRODUCT_UPDATE");
    }

    private int sanitizeReorderLevel(Integer reorderLevel) {
        return Math.max(0, reorderLevel != null ? reorderLevel : 0);
    }

}
