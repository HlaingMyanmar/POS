package org.sspd.servicemgmt.stockoptions.productoptions.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.brandoptions.model.Brand;
import org.sspd.servicemgmt.brandoptions.repository.BrandRepository;
import org.sspd.servicemgmt.categoryoptions.model.Category;
import org.sspd.servicemgmt.categoryoptions.repository.CategoryRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ImportResultDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ProductDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.enums.ProductType;
import org.sspd.servicemgmt.stockoptions.productoptions.mapper.ProductMapper;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.enums.ManufacturingStatus;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.repository.ManufacturingOrderRepository;
import org.sspd.servicemgmt.unitsoptions.model.Unit;
import org.sspd.servicemgmt.unitsoptions.repository.UnitRepository;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.PriceHistoryDTO;
import org.sspd.servicemgmt.stockoptions.productoptions.dto.ReorderSuggestionDTO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ManufacturingOrderRepository manufacturingOrderRepository;
    private final PurchaseRepository purchaseRepository;

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
        archive(id);
    }

    @PreAuthorize("hasAnyAuthority('CAN_ACCESS_PRODUCT_DELETE', 'CAN_ACCESS_PRODUCT_UPDATE')")
    @Transactional
    public void archive(Integer id){
        Product existingEntity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found with id " + id));
        if (manufacturingOrderRepository.existsByFinishedProductIdAndStatus(id, ManufacturingStatus.COMPLETED)) {
            throw new IllegalStateException("ထုတ်လုပ်ရေးမှ ဖန်တီးထားသော ကုန်ပစ္စည်းကို archive မလုပ်ရပါ။ ထုတ်လုပ်ရေး မှတ်တမ်းကို ဦးစွာ စစ်ဆေးပါ။");
        }
        existingEntity.setArchived(Boolean.TRUE);
        productRepository.save(existingEntity);
        messagingTemplate.convertAndSend(PRODUCT_TOPIC, "PRODUCT_ARCHIVED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @Transactional
    public ProductDTO setArchived(Integer id, boolean archived){
        Product existingEntity = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product Not Found with id " + id));
        existingEntity.setArchived(archived);
        Product savedEntity = productRepository.save(existingEntity);
        messagingTemplate.convertAndSend(PRODUCT_TOPIC, archived ? "PRODUCT_ARCHIVED" : "PRODUCT_RESTORED");
        return toDtoWithAvailability(savedEntity);
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

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @Transactional(readOnly = true)
    public List<ReorderSuggestionDTO> reorderSuggestions() {
        Map<Integer, Purchase> latestPurchaseByProduct = latestPurchaseByProduct();
        return findAll().stream()
                .filter(product -> (product.getReorderLevel() != null && product.getReorderLevel() > 0)
                        && (product.getStockQty() == null ? 0 : product.getStockQty()) <= product.getReorderLevel())
                .map(product -> {
                    int current = product.getStockQty() == null ? 0 : product.getStockQty();
                    int reorderLevel = product.getReorderLevel() == null ? 0 : product.getReorderLevel();
                    Purchase latest = latestPurchaseByProduct.get(product.getId());
                    return ReorderSuggestionDTO.builder()
                            .productId(product.getId())
                            .productCode(product.getProductCode())
                            .productName(product.getName())
                            .currentStock(current)
                            .reorderLevel(reorderLevel)
                            .suggestedQuantity(Math.max(1, reorderLevel * 2 - current))
                            .supplierId(latest != null && latest.getSupplier() != null ? latest.getSupplier().getId() : null)
                            .supplierName(latest != null && latest.getSupplier() != null ? latest.getSupplier().getName() : "No supplier history")
                            .currentCost(product.getCostPrice())
                            .build();
                })
                .sorted(Comparator.comparing(ReorderSuggestionDTO::getSupplierName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(ReorderSuggestionDTO::getProductName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @Transactional(readOnly = true)
    public List<PriceHistoryDTO> priceHistory(Integer productId) {
        List<Purchase> purchases = purchaseRepository.findAll().stream()
                .filter(purchase -> purchase.getDetails() != null && purchase.getDetails().stream()
                        .anyMatch(detail -> detail.getProduct() != null && productId.equals(detail.getProduct().getId())))
                .sorted(Comparator.comparing(Purchase::getPurchaseDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<PriceHistoryDTO> history = new ArrayList<>();
        int cumulativeQty = 0;
        BigDecimal cumulativeValue = BigDecimal.ZERO;
        for (Purchase purchase : purchases) {
            purchase.getDetails().stream()
                    .filter(detail -> detail.getProduct() != null && productId.equals(detail.getProduct().getId()))
                    .forEach(detail -> {
                        int qty = detail.getQty() == null ? 0 : detail.getQty();
                        BigDecimal cost = detail.getUnitCost() == null ? BigDecimal.ZERO : detail.getUnitCost();
                        int nextQty = cumulativeQty + qty;
                        BigDecimal nextValue = cumulativeValue.add(cost.multiply(BigDecimal.valueOf(qty)));
                        BigDecimal average = nextQty > 0 ? nextValue.divide(BigDecimal.valueOf(nextQty), 2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO;
                        history.add(PriceHistoryDTO.builder()
                                .purchaseId(purchase.getId())
                                .purchaseCode(purchase.getPurchaseCode())
                                .purchaseDate(purchase.getPurchaseDate())
                                .supplierId(purchase.getSupplier() != null ? purchase.getSupplier().getId() : null)
                                .supplierName(purchase.getSupplier() != null ? purchase.getSupplier().getName() : null)
                                .quantity(qty)
                                .unitCost(cost)
                                .weightedAverageCost(average)
                                .build());
                    });
        }
        Collections.reverse(history);
        return history;
    }

    private Map<Integer, Purchase> latestPurchaseByProduct() {
        Map<Integer, Purchase> latest = new HashMap<>();
        purchaseRepository.findAll().forEach(purchase -> {
            if (purchase.getDetails() == null) return;
            purchase.getDetails().forEach(detail -> {
                if (detail.getProduct() == null) return;
                Purchase existing = latest.get(detail.getProduct().getId());
                if (existing == null || (purchase.getPurchaseDate() != null && purchase.getPurchaseDate().isAfter(existing.getPurchaseDate()))) {
                    latest.put(detail.getProduct().getId(), purchase);
                }
            });
        });
        return latest;
    }


    private ProductDTO toDtoWithAvailability(Product entity) {
        ProductDTO dto = mapper.toDto(entity);
        if (Boolean.TRUE.equals(entity.getHasSerial())) {
            Long count = productSerialRepository.countByProductIdAndStatus(entity.getId(), SerialStatus.Available);
            int available = count != null ? count.intValue() : 0;
            dto.setAvailableSerialCount(available);
            dto.setStockQty(available);
            int rawQty = entity.getStockQty() != null ? entity.getStockQty() : 0;
            long totalSerials = productSerialRepository.countByProductId(entity.getId());
            dto.setUnlinkedQty(Math.max(0, rawQty - (int) totalSerials));
        } else {
            dto.setAvailableSerialCount(null);
            dto.setStockQty(Math.max(0, (entity.getStockQty() == null ? 0 : entity.getStockQty())
                    - (entity.getQuarantinedQty() == null ? 0 : entity.getQuarantinedQty())));
            dto.setUnlinkedQty(0);
        }
        dto.setQuarantinedQty(entity.getQuarantinedQty() == null ? 0 : entity.getQuarantinedQty());
        dto.setHasSerial(entity.getHasSerial());
        int reorderLevel = sanitizeReorderLevel(entity.getReorderLevel());
        dto.setReorderLevel(reorderLevel);
        int stock = dto.getStockQty() != null ? dto.getStockQty() : 0;
        dto.setShortageQty(Math.max(0, reorderLevel - stock));
        return dto;
    }

    /**
     * Retroactively assign serial numbers to stock that is not linked to serial records.
     * Converts qty-only products to serial-tracked, or repairs serial-tracked products with leftover qty stock.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_UPDATE')")
    @Transactional
    public ProductDTO assignSerials(Integer productId, List<String> serialNumbers, Integer warrantyMonths) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        int rawQty = product.getStockQty() != null ? product.getStockQty() : 0;
        long totalSerials = Boolean.TRUE.equals(product.getHasSerial())
                ? productSerialRepository.countByProductId(product.getId())
                : 0;
        int currentQty = Math.max(0, rawQty - (int) totalSerials);
        if (currentQty <= 0) {
            throw new RuntimeException("Product has no unlinked stock to assign serials to.");
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

    // ── Excel Export ──────────────────────────────────────────────────────────

    private static final String[] HEADERS = {
        "ကုန်ပစ္စည်းကုဒ်", "ကုန်ပစ္စည်းနာမည်*", "ကုန်ပစ္စည်းအမျိုးအစား*",
        "ဘရန်း", "ကဏ္ဍ", "ယူနစ်",
        "ရောင်းဈေး", "ဝယ်ဈေး", "Stock အရေအတွက်",
        "Serial ရှိမရှိ (Yes/No)", "Reorder Level",
        "အာမခံ လ (Months)", "အာမခံ အသေးစိတ်", "မှတ်ချက်"
    };

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    @Transactional(readOnly = true)
    public byte[] exportExcel() throws IOException {
        List<ProductDTO> products = findAll();
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("ကုန်ပစ္စည်းစာရင်း");

            // Header style
            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            // Data style
            XSSFCellStyle dataStyle = wb.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            int rowNum = 1;
            for (ProductDTO p : products) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nullStr(p.getProductCode()));
                row.createCell(1).setCellValue(nullStr(p.getName()));
                row.createCell(2).setCellValue(p.getProductType() != null ? p.getProductType().name() : "New");
                row.createCell(3).setCellValue(nullStr(p.getBrandName()));
                row.createCell(4).setCellValue(nullStr(p.getCategoryName()));
                row.createCell(5).setCellValue(nullStr(p.getUnitName()));
                row.createCell(6).setCellValue(p.getSellingPrice() != null ? p.getSellingPrice().doubleValue() : 0);
                row.createCell(7).setCellValue(p.getCostPrice() != null ? p.getCostPrice().doubleValue() : 0);
                row.createCell(8).setCellValue(p.getStockQty() != null ? p.getStockQty() : 0);
                row.createCell(9).setCellValue(Boolean.TRUE.equals(p.getHasSerial()) ? "Yes" : "No");
                row.createCell(10).setCellValue(p.getReorderLevel() != null ? p.getReorderLevel() : 0);
                row.createCell(11).setCellValue(p.getWarrantyMonths() != null ? p.getWarrantyMonths() : 0);
                row.createCell(12).setCellValue(nullStr(p.getWarrantyTerms()));
                row.createCell(13).setCellValue(nullStr(p.getRemark()));
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Import Template ───────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_READ')")
    public byte[] downloadTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("ကုန်ပစ္စည်းထည့်သွင်းရန်");

            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            XSSFCellStyle noteStyle = wb.createCellStyle();
            XSSFFont noteFont = wb.createFont();
            noteFont.setItalic(true);
            noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            noteStyle.setFont(noteFont);

            // Note row
            Row noteRow = sheet.createRow(0);
            Cell noteCell = noteRow.createCell(0);
            noteCell.setCellValue("မှတ်ချက်: * ဖြင့်မှတ်သားထားသောကော်လံများ မဖြည့်မဖြစ်ပါ။ ကုန်ပစ္စည်းကုဒ် ကော်လံသည် import တွင် အသုံးမဝင်ဘဲ ရည်ညွှန်းရုံသာဖြစ်သည်။");
            noteCell.setCellStyle(noteStyle);

            // Header row
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5500);
            }

            // Sample row
            Row sample = sheet.createRow(2);
            sample.createCell(0).setCellValue("(auto)");
            sample.createCell(1).setCellValue("Samsung Galaxy S24");
            sample.createCell(2).setCellValue("New");
            sample.createCell(3).setCellValue("Samsung");
            sample.createCell(4).setCellValue("Phone");
            sample.createCell(5).setCellValue("pcs");
            sample.createCell(6).setCellValue(1500000);
            sample.createCell(7).setCellValue(1200000);
            sample.createCell(8).setCellValue(10);
            sample.createCell(9).setCellValue("No");
            sample.createCell(10).setCellValue(5);
            sample.createCell(11).setCellValue(12);
            sample.createCell(12).setCellValue("မူရင်းထုတ်လုပ်သူ အာမခံ");
            sample.createCell(13).setCellValue("");

            // Dropdown for Product Type
            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            DataValidationConstraint typeConstraint = dvHelper.createExplicitListConstraint(new String[]{"New", "Second_New", "Second"});
            CellRangeAddressList typeRange = new CellRangeAddressList(2, 1000, 2, 2);
            DataValidation typeValidation = dvHelper.createValidation(typeConstraint, typeRange);
            sheet.addValidationData(typeValidation);

            // Dropdown for Has Serial
            DataValidationConstraint serialConstraint = dvHelper.createExplicitListConstraint(new String[]{"Yes", "No"});
            CellRangeAddressList serialRange = new CellRangeAddressList(2, 1000, 9, 9);
            DataValidation serialValidation = dvHelper.createValidation(serialConstraint, serialRange);
            sheet.addValidationData(serialValidation);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Excel Import ──────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('CAN_ACCESS_PRODUCT_CREATE')")
    @Transactional
    public ImportResultDTO importExcel(MultipartFile file) throws IOException {
        ImportResultDTO result = new ImportResultDTO();

        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            // Detect if template (has note row at 0, headers at 1, data from 2)
            // or plain export (headers at 0, data from 1)
            int dataStartRow = detectDataStartRow(sheet);

            for (int r = dataStartRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row, 1, 13)) continue;

                int displayRow = r + 1;
                try {
                    String name = getCellString(row, 1);
                    if (name.isBlank()) {
                        result.getErrors().add(new ImportResultDTO.RowError(displayRow, "ကုန်ပစ္စည်းနာမည် မပါဝင်ပါ"));
                        result.setErrorCount(result.getErrorCount() + 1);
                        continue;
                    }

                    if (productRepository.existsByName(name)) {
                        result.getErrors().add(new ImportResultDTO.RowError(displayRow, "'" + name + "' ဆိုသောကုန်ပစ္စည်း ရှိပြီးသားဖြစ်သည်"));
                        result.setErrorCount(result.getErrorCount() + 1);
                        continue;
                    }

                    String typeStr = getCellString(row, 2);
                    ProductType productType;
                    try {
                        productType = typeStr.isBlank() ? ProductType.New : ProductType.valueOf(typeStr.trim());
                    } catch (IllegalArgumentException e) {
                        result.getErrors().add(new ImportResultDTO.RowError(displayRow, "ကုန်ပစ္စည်းအမျိုးအစား မမှန်ကန်ပါ: " + typeStr + " (New / Second_New / Second သာ ခွင့်ပြု)"));
                        result.setErrorCount(result.getErrorCount() + 1);
                        continue;
                    }

                    String brandName = getCellString(row, 3);
                    String categoryName = getCellString(row, 4);
                    String unitName = getCellString(row, 5);

                    Brand brand = null;
                    if (!brandName.isBlank()) {
                        brand = brandRepository.findByNameIgnoreCase(brandName).orElse(null);
                        if (brand == null) {
                            result.getErrors().add(new ImportResultDTO.RowError(displayRow, "ဘရန်း '" + brandName + "' မတွေ့ပါ"));
                            result.setErrorCount(result.getErrorCount() + 1);
                            continue;
                        }
                    }

                    Category category = null;
                    if (!categoryName.isBlank()) {
                        category = categoryRepository.findByNameIgnoreCase(categoryName).orElse(null);
                        if (category == null) {
                            result.getErrors().add(new ImportResultDTO.RowError(displayRow, "ကဏ္ဍ '" + categoryName + "' မတွေ့ပါ"));
                            result.setErrorCount(result.getErrorCount() + 1);
                            continue;
                        }
                    }

                    Unit unit = null;
                    if (!unitName.isBlank()) {
                        unit = unitRepository.findByUnitNameIgnoreCase(unitName).orElse(null);
                        if (unit == null) {
                            result.getErrors().add(new ImportResultDTO.RowError(displayRow, "ယူနစ် '" + unitName + "' မတွေ့ပါ"));
                            result.setErrorCount(result.getErrorCount() + 1);
                            continue;
                        }
                    }

                    double sellingPriceVal = getCellNumeric(row, 6);
                    double costPriceVal = getCellNumeric(row, 7);
                    int stockQty = (int) getCellNumeric(row, 8);
                    boolean hasSerial = getCellString(row, 9).equalsIgnoreCase("yes");
                    int reorderLevel = (int) getCellNumeric(row, 10);
                    int warrantyMonths = (int) getCellNumeric(row, 11);
                    String warrantyTerms = getCellString(row, 12);
                    String remark = getCellString(row, 13);

                    Product product = new Product();
                    product.setName(name);
                    product.setProductCode("PENDING");
                    product.setProductType(productType);
                    product.setBrand(brand);
                    product.setCategory(category);
                    product.setUnit(unit);
                    product.setSellingPrice(BigDecimal.valueOf(sellingPriceVal));
                    product.setCostPrice(BigDecimal.valueOf(costPriceVal));
                    product.setStockQty(stockQty);
                    product.setHasSerial(hasSerial);
                    product.setReorderLevel(sanitizeReorderLevel(reorderLevel));
                    product.setWarrantyMonths(warrantyMonths);
                    product.setWarrantyTerms(warrantyTerms.isBlank() ? null : warrantyTerms);
                    product.setRemark(remark.isBlank() ? null : remark);

                    Product saved = productRepository.save(product);
                    saved.setProductCode("PRD-" + String.format("%06d", saved.getId()));
                    productRepository.save(saved);

                    result.setSuccessCount(result.getSuccessCount() + 1);

                } catch (Exception ex) {
                    result.getErrors().add(new ImportResultDTO.RowError(displayRow, "မမျှော်လင့်သောအမှား: " + ex.getMessage()));
                    result.setErrorCount(result.getErrorCount() + 1);
                }
            }
        }

        if (result.getSuccessCount() > 0) {
            messagingTemplate.convertAndSend(PRODUCT_TOPIC, "PRODUCT_CREATED");
        }
        return result;
    }

    private int detectDataStartRow(Sheet sheet) {
        Row row0 = sheet.getRow(0);
        if (row0 == null) return 1;
        String first = getCellString(row0, 0);
        // Template has note at row 0, headers at row 1
        if (first.startsWith("မှတ်ချက်")) return 2;
        // Export has headers at row 0
        return 1;
    }

    private boolean isRowEmpty(Row row, int fromCol, int toCol) {
        for (int c = fromCol; c <= toCol; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String v = getCellString(row, c);
                if (!v.isBlank()) return false;
            }
        }
        return true;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) yield String.valueOf((long) d);
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private double getCellNumeric(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        try { return Double.parseDouble(getCellString(row, col)); }
        catch (NumberFormatException e) { return 0; }
    }

    private String nullStr(String s) { return s != null ? s : ""; }
}
