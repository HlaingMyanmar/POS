package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.api.PageResponse;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto.PurchaseDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderReceiveDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderReceiveResultDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.mapper.PurchaseOrderMapper;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.POStatus;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrder;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrderDetail;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository.PurchaseOrderRepository;
import org.sspd.servicemgmt.purchaseoptions.service.PurchaseService;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final String PO_TOPIC = "/topic/purchase-order";

    private final PurchaseOrderRepository poRepository;
    private final SupplierRepository supplierRepository;
    private final StaffRepository staffRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderMapper mapper;
    private final PurchaseService purchaseService;
    private final SimpMessagingTemplate messagingTemplate;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_CREATE')")
    @Transactional
    public PurchaseOrderDTO save(PurchaseOrderDTO dto) {
        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));

        if (dto.getDetails() == null || dto.getDetails().isEmpty())
            throw new RuntimeException("Purchase Order must have at least one line.");

        PurchaseOrder po = mapper.toEntity(dto);
        po.setSupplier(supplier);
        po.setStaff(staff);
        po.setPoCode("PENDING");
        po.setStatus(POStatus.OPEN);
        if (po.getOrderDate() == null) po.setOrderDate(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderDetail> details = new ArrayList<>();
        for (PurchaseOrderDetailDTO dDto : dto.getDetails()) {
            Product product = productRepository.findById(dDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            int qty = dDto.getQty() != null ? dDto.getQty() : 0;
            BigDecimal unitCost = dDto.getUnitCost() != null ? dDto.getUnitCost() : BigDecimal.ZERO;
            if (qty <= 0) throw new RuntimeException("Qty must be greater than zero for: " + product.getName());
            if (unitCost.compareTo(BigDecimal.ZERO) < 0)
                throw new RuntimeException("Unit cost cannot be negative for: " + product.getName());
            BigDecimal subtotal = unitCost.multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);
            details.add(PurchaseOrderDetail.builder()
                    .purchaseOrder(po).product(product)
                    .qty(qty).receivedQty(0)
                    .unitCost(unitCost).subtotal(subtotal)
                    .build());
        }

        po.setDetails(details);
        po.setTotalAmount(total);

        PurchaseOrder saved = poRepository.save(po);
        saved.setPoCode(generatePoCode(saved.getId()));
        saved = poRepository.save(saved);

        messagingTemplate.convertAndSend(PO_TOPIC, "PO_CREATED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderDTO> findAll(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return PageResponse.of(poRepository.findBySearch(
                search == null ? "" : search.trim(), pageable).map(mapper::toDto));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @Transactional(readOnly = true)
    public PurchaseOrderDTO findById(Integer id) {
        return mapper.toDto(getEntity(id));
    }

    /**
     * Header/detail edits allowed only while OPEN and nothing received yet.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_UPDATE')")
    @Transactional
    public PurchaseOrderDTO update(Integer id, PurchaseOrderDTO dto) {
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() != POStatus.OPEN)
            throw new RuntimeException("Only OPEN purchase orders can be edited.");
        boolean anyReceived = po.getDetails().stream()
                .anyMatch(d -> d.getReceivedQty() != null && d.getReceivedQty() > 0);
        if (anyReceived)
            throw new RuntimeException("Cannot edit — some quantities already received. Cancel remaining instead.");

        if (dto.getExpectedDate() != null) po.setExpectedDate(dto.getExpectedDate());
        if (dto.getRemark() != null) po.setRemark(dto.getRemark());

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            po.getDetails().clear();
            BigDecimal total = BigDecimal.ZERO;
            for (PurchaseOrderDetailDTO dDto : dto.getDetails()) {
                Product product = productRepository.findById(dDto.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
                int qty = dDto.getQty() != null ? dDto.getQty() : 0;
                BigDecimal unitCost = dDto.getUnitCost() != null ? dDto.getUnitCost() : BigDecimal.ZERO;
                if (qty <= 0) throw new RuntimeException("Qty must be greater than zero for: " + product.getName());
                BigDecimal subtotal = unitCost.multiply(BigDecimal.valueOf(qty));
                total = total.add(subtotal);
                po.getDetails().add(PurchaseOrderDetail.builder()
                        .purchaseOrder(po).product(product)
                        .qty(qty).receivedQty(0)
                        .unitCost(unitCost).subtotal(subtotal)
                        .build());
            }
            po.setTotalAmount(total);
        }

        PurchaseOrder saved = poRepository.save(po);
        messagingTemplate.convertAndSend(PO_TOPIC, "PO_UPDATED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_DELETE')")
    @Transactional
    public void cancel(Integer id) {
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() == POStatus.RECEIVED)
            throw new RuntimeException("Fully received order cannot be cancelled.");
        if (po.getDetails().stream().anyMatch(d -> d.getReceivedQty() != null && d.getReceivedQty() > 0))
            throw new RuntimeException("Partially received order cannot be cancelled.");
        po.setStatus(POStatus.CANCELLED);
        poRepository.save(po);
        messagingTemplate.convertAndSend(PO_TOPIC, "PO_CANCELLED");
    }

    /**
     * ✅ Goods Receipt — receives PO lines, creates the actual Purchase voucher
     * (stock + serials + accounting run inside PurchaseService.save) and updates
     * received quantities / PO status.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_RECEIVE')")
    @Transactional
    public PurchaseOrderReceiveResultDTO receive(Integer id, PurchaseOrderReceiveDTO receive) {
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() != POStatus.OPEN && po.getStatus() != POStatus.PARTIAL)
            throw new RuntimeException("Only OPEN or PARTIAL orders can be received.");

        Map<Integer, PurchaseOrderReceiveDTO.ReceiveLine> lineByDetailId = receive.getLines() == null
                ? Map.of()
                : receive.getLines().stream()
                    .filter(l -> l.getDetailId() != null)
                    .collect(Collectors.toMap(PurchaseOrderReceiveDTO.ReceiveLine::getDetailId, Function.identity(), (a, b) -> b));

        List<PurchaseOrderDetail> toReceive = po.getDetails().stream()
                .filter(d -> {
                    Integer orderedRemaining = (d.getQty() != null ? d.getQty() : 0) - (d.getReceivedQty() != null ? d.getReceivedQty() : 0);
                    if (orderedRemaining <= 0) return false;
                    if (!lineByDetailId.isEmpty()) {
                        var line = lineByDetailId.get(d.getId());
                        return line != null && line.getQty() != null && line.getQty() > 0;
                    }
                    return true; // no explicit lines → receive everything remaining
                })
                .toList();

        if (toReceive.isEmpty())
            throw new RuntimeException("Nothing left to receive on this order.");

        PurchaseDTO purchaseDto = new PurchaseDTO();
        purchaseDto.setSupplierId(po.getSupplier().getId());
        purchaseDto.setStaffId(receive.getStaffId() != null ? receive.getStaffId() : po.getStaff().getId());
        purchaseDto.setPurchaseDate(LocalDateTime.now());
        purchaseDto.setDueDate(receive.getDueDate());
        purchaseDto.setDiscountAmount(receive.getDiscountAmount());
        purchaseDto.setTaxAmount(receive.getTaxAmount());
        purchaseDto.setOtherCharges(receive.getOtherCharges());
        purchaseDto.setRemark(receive.getRemark() != null ? receive.getRemark()
                : ("Goods Receipt for PO " + po.getPoCode()));
        purchaseDto.setPaymentMethodId(receive.getPaymentMethodId());
        purchaseDto.setTransactionNo(receive.getTransactionNo());
        purchaseDto.setPayments(receive.getPayments());

        List<PurchaseDetailDTO> details = new ArrayList<>();
        for (PurchaseOrderDetail pod : toReceive) {
            var line = lineByDetailId.get(pod.getId());
            int qty = line != null && line.getQty() != null ? line.getQty()
                    : (pod.getQty() - safeInt(pod.getReceivedQty()));

            int already = safeInt(pod.getReceivedQty());
            if (already + qty > safeInt(pod.getQty()))
                throw new RuntimeException("Receive quantity exceeds ordered remaining for: "
                        + pod.getProduct().getName());

            Product product = pod.getProduct();
            int bulkMonths = line != null && line.getWarrantyMonths() != null ? line.getWarrantyMonths()
                    : (product.getWarrantyMonths() != null ? product.getWarrantyMonths() : 0);
            List<Integer> itemWarranties = line != null && line.getItemWarranties() != null && !line.getItemWarranties().isEmpty()
                    ? line.getItemWarranties()
                    : java.util.stream.IntStream.range(0, qty).mapToObj(i -> bulkMonths).toList();

            details.add(PurchaseDetailDTO.builder()
                    .productId(product.getId())
                    .qty(qty)
                    .unitCost(pod.getUnitCost())
                    .subtotal(pod.getUnitCost().multiply(BigDecimal.valueOf(qty)))
                    .warrantyMonths(bulkMonths)
                    .itemWarranties(itemWarranties)
                    .serialNumbers(line != null && line.getSerialNumbers() != null ? line.getSerialNumbers() : null)
                    .serialConditions(line != null && line.getSerialConditions() != null ? line.getSerialConditions() : null)
                    .serialPhotos(line != null && line.getSerialPhotos() != null ? line.getSerialPhotos() : null)
                    .build());
        }
        purchaseDto.setDetails(details);

        PurchaseDTO createdPurchase = purchaseService.save(purchaseDto);

        // Update received quantities + status
        for (PurchaseOrderDetail pod : toReceive) {
            var line = lineByDetailId.get(pod.getId());
            int qty = line != null && line.getQty() != null ? line.getQty()
                    : (safeInt(pod.getQty()) - safeInt(pod.getReceivedQty()));
            pod.setReceivedQty(safeInt(pod.getReceivedQty()) + qty);
        }
        boolean allReceived = po.getDetails().stream()
                .allMatch(d -> safeInt(d.getReceivedQty()) >= safeInt(d.getQty()));
        po.setStatus(allReceived ? POStatus.RECEIVED : POStatus.PARTIAL);
        PurchaseOrder savedPo = poRepository.save(po);

        messagingTemplate.convertAndSend(PO_TOPIC, "PO_RECEIVED");
        return PurchaseOrderReceiveResultDTO.builder()
                .order(mapper.toDto(savedPo))
                .purchase(createdPurchase)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────

    private PurchaseOrder getEntity(Integer id) {
        return poRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + id));
    }

    private String generatePoCode(Integer id) {
        return String.format("PO-%05d", id);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }
}
