package org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.dto.StockAdjustmentDTO;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.mapper.StockAdjustmentMapper;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model.AdjustmentType;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model.StockAdjustment;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.repository.StockAdjustmentRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.sspd.servicemgmt.api.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockAdjustmentRepository adjustmentRepository;
    private final ProductRepository productRepository;
    private final ProductSerialRepository productSerialRepository;
    private final StaffRepository staffRepository;
    private final StockMovementService stockMovementService;
    private final JournalWriter journalWriter;
    private final StockAdjustmentMapper mapper;
    private final AccountResolver accounts;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String STOCK_ADJUSTMENT_TOPIC = "/topic/stock-adjustment";

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_ADJUSTMENT_CREATE')")
    @Transactional
    public StockAdjustmentDTO save(StockAdjustmentDTO dto) {

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        boolean isSerialProduct = Boolean.TRUE.equals(product.getHasSerial());
        List<String> serials = dto.getSerialNumbers() != null && !dto.getSerialNumbers().trim().isEmpty()
                ? List.of(dto.getSerialNumbers().split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList()
                : List.of();
        int qtyChange = dto.getQtyChange();

        if (!serials.isEmpty()) {
            if (Math.abs(qtyChange) != serials.size()) {
                throw new RuntimeException("Qty change must match serial count for product: " + product.getName());
            }
        }

        int qtyBefore;
        if (!serials.isEmpty()) {
            qtyBefore = productSerialRepository.countByProductIdAndStatus(product.getId(), SerialStatus.Available).intValue();
        } else {
            qtyBefore = product.getStockQty() != null ? product.getStockQty() : 0;
        }
        int qtyAfter = qtyBefore;

        switch (dto.getAdjustmentType()) {
            case DAMAGE, LOSS -> {
                SerialStatus targetStatus = dto.getAdjustmentType() == AdjustmentType.DAMAGE ? SerialStatus.Damaged : SerialStatus.Lost;
                if (!serials.isEmpty()) {
                    for (String sn : serials) {
                        ProductSerial serial = productSerialRepository.findBySerialNumber(sn)
                                .orElseThrow(() -> new RuntimeException("Serial number '" + sn + "' not found"));
                        if (!serial.getProduct().getId().equals(product.getId())) {
                            throw new RuntimeException("Serial number '" + sn + "' does not belong to product: " + product.getName());
                        }
                        if (serial.getStatus() != SerialStatus.Available) {
                            throw new RuntimeException("Serial number '" + sn + "' is not Available");
                        }
                        serial.setStatus(targetStatus);
                        productSerialRepository.save(serial);
                    }
                    qtyAfter = qtyBefore - serials.size();
                } else {
                    if (qtyChange >= 0) {
                        throw new RuntimeException(dto.getAdjustmentType() + " must have negative qty change for non-serial product.");
                    }
                    qtyAfter = qtyBefore + qtyChange;
                    if (qtyAfter < 0) {
                        throw new RuntimeException("Stock cannot go negative. Current: " + qtyBefore + ", Change: " + qtyChange);
                    }
                    product.setStockQty(qtyAfter);
                    productRepository.save(product);
                }
            }
            case FOUND -> {
                if (!serials.isEmpty()) {
                    for (String sn : serials) {
                        ProductSerial serial = productSerialRepository.findBySerialNumber(sn)
                                .orElseThrow(() -> new RuntimeException("Serial number '" + sn + "' not found"));
                        if (!serial.getProduct().getId().equals(product.getId())) {
                            throw new RuntimeException("Serial number '" + sn + "' does not belong to product: " + product.getName());
                        }
                        if (serial.getStatus() != SerialStatus.Damaged && serial.getStatus() != SerialStatus.Lost) {
                            throw new RuntimeException("Serial number '" + sn + "' must be Damaged or Lost to be FOUND");
                        }
                        serial.setStatus(SerialStatus.Available);
                        productSerialRepository.save(serial);
                    }
                    qtyAfter = qtyBefore + serials.size();
                } else {
                    if (qtyChange <= 0) {
                        throw new RuntimeException("FOUND must have positive qty change for non-serial product.");
                    }
                    qtyAfter = qtyBefore + qtyChange;
                    product.setStockQty(qtyAfter);
                    productRepository.save(product);
                }
            }
            case CORRECTION -> {
                if (!serials.isEmpty()) {
                    for (String sn : serials) {
                        ProductSerial serial = productSerialRepository.findBySerialNumber(sn)
                                .orElseThrow(() -> new RuntimeException("Serial number '" + sn + "' not found"));
                        if (!serial.getProduct().getId().equals(product.getId())) {
                            throw new RuntimeException("Serial number '" + sn + "' does not belong to product: " + product.getName());
                        }
                        if (serial.getStatus() == SerialStatus.Sold) {
                            throw new RuntimeException("Serial '" + sn + "' is Sold and cannot be corrected. Please process a Sale Return first.");
                        }
                        serial.setStatus(SerialStatus.Available);
                        productSerialRepository.save(serial);
                    }
                    qtyAfter = qtyBefore + qtyChange;
                } else {
                    qtyAfter = qtyBefore + qtyChange;
                    if (qtyAfter < 0) {
                        throw new RuntimeException("Stock cannot go negative. Current: " + qtyBefore + ", Change: " + qtyChange);
                    }
                    product.setStockQty(qtyAfter);
                    productRepository.save(product);
                }
            }
        }

        StockAdjustment entity = mapper.toEntity(dto);
        entity.setProduct(product);
        entity.setStaff(staff);
        entity.setQtyBefore(qtyBefore);
        entity.setQtyAfter(qtyAfter);
        entity.setCreatedAt(LocalDateTime.now());

        // Override qtyChange for serial adjustments
        if (!serials.isEmpty()) {
            int correctedQtyChange = switch (dto.getAdjustmentType()) {
                case DAMAGE, LOSS -> -serials.size();
                case FOUND -> serials.size();
                case CORRECTION -> qtyAfter - qtyBefore;
            };
            entity.setQtyChange(correctedQtyChange);
        }

        StockAdjustment saved = adjustmentRepository.save(entity);

        // Stock Movement
        int movementQty = isSerialProduct ? serials.size() : Math.abs(qtyChange);
        stockMovementService.recordMovement(StockMovement.builder()
                .product(product)
                .movementType(MovementType.ADJUST)
                .qty(movementQty)
                .referenceType("StockAdjustment")
                .referenceId(saved.getId())
                .build());

        // Journal
        createAdjustmentJournal(saved, product, staff);

        messagingTemplate.convertAndSend(STOCK_ADJUSTMENT_TOPIC, "STOCK_ADJUSTMENT_CREATED");

        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_ADJUSTMENT_READ')")
    @Transactional(readOnly = true)
    public PageResponse<StockAdjustmentDTO> findAll(String search, int page, int size) {
        return PageResponse.of(
            adjustmentRepository.findBySearch(search, PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(mapper::toDto)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_STOCK_ADJUSTMENT_READ')")
    @Transactional(readOnly = true)
    public StockAdjustmentDTO findById(Integer id) {
        return mapper.toDto(adjustmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + id)));
    }

    private void createAdjustmentJournal(StockAdjustment adj, Product product, Staff staff) {

        // Guard 1: cost price check — CORRECTION without cost price skips journal only
        BigDecimal costPrice = product.getCostPrice();
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            if (adj.getAdjustmentType() == AdjustmentType.CORRECTION) return;
            throw new RuntimeException(
                "Cost price not set for product: " + product.getName() +
                ". Please set cost price before stock adjustment.");
        }

        // Guard 2: qty check
        if (adj.getQtyChange() == 0 && adj.getAdjustmentType() != AdjustmentType.CORRECTION) {
            throw new RuntimeException(
                "QtyChange is 0 for adjustment id: " + adj.getId() +
                ". Journal cannot be created with zero amount.");
        }

        BigDecimal amount = costPrice.multiply(BigDecimal.valueOf(Math.abs(adj.getQtyChange())));

        List<JournalDetailDTO> details = new ArrayList<>();
        String description;
        String refSuffix;

        switch (adj.getAdjustmentType()) {

            case DAMAGE, LOSS -> {
                // DR: Inventory Loss (EXP-011)
                // CR: Inventory / Stock (ASS-005)
                description = adj.getAdjustmentType() + " - " + product.getName();
                refSuffix   = adj.getAdjustmentType().name();

                details.add(debit(accounts.inventoryLoss().getId(), amount));
                details.add(credit(accounts.inventory().getId(), amount));
            }

            case FOUND -> {
                // DR: Inventory / Stock (ASS-005)
                // CR: Inventory Gain (INC-006)
                description = "FOUND - " + product.getName();
                refSuffix   = "FOUND";

                details.add(debit(accounts.inventory().getId(), amount));
                details.add(credit(accounts.inventoryGain().getId(), amount));
            }

            case CORRECTION -> {
                if (adj.getQtyChange() == 0) return; // Skip journal for zero change
                if (adj.getQtyChange() > 0) {
                    // DR: Inventory / Stock (ASS-005)
                    // CR: Inventory Over (INC-008)
                    description = "CORRECTION(+) - " + product.getName();
                    refSuffix   = "CORR-OVER";

                    details.add(debit(accounts.inventory().getId(), amount));
                    details.add(credit(accounts.inventoryOver().getId(), amount));
                } else {
                    // DR: Inventory Short (EXP-012)
                    // CR: Inventory / Stock (ASS-005)
                    description = "CORRECTION(-) - " + product.getName();
                    refSuffix   = "CORR-SHORT";

                    details.add(debit(accounts.inventoryShort().getId(), amount));
                    details.add(credit(accounts.inventory().getId(), amount));
                }
            }

            default -> throw new RuntimeException("Unknown adjustment type: " + adj.getAdjustmentType());
        }

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo("ADJ-" + adj.getId() + "-" + refSuffix);
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription(description +
                " | Qty: " + adj.getQtyChange() +
                " | Before: " + adj.getQtyBefore() +
                " → After: " + adj.getQtyAfter());
        journalDTO.setStaffId(staff != null ? staff.getId() : null);
        journalDTO.setDetails(details);

        journalWriter.write(journalDTO);
    }

    private JournalDetailDTO debit(Integer accountId, BigDecimal amount) {
        JournalDetailDTO dto = new JournalDetailDTO();
        dto.setAccountId(accountId);
        dto.setDebit(amount);
        dto.setCredit(BigDecimal.ZERO);
        return dto;
    }

    private JournalDetailDTO credit(Integer accountId, BigDecimal amount) {
        JournalDetailDTO dto = new JournalDetailDTO();
        dto.setAccountId(accountId);
        dto.setDebit(BigDecimal.ZERO);
        dto.setCredit(amount);
        return dto;
    }
}
