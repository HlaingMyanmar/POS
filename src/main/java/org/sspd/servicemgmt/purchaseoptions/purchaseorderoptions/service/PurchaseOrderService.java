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
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto.PurchaseDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderReceiveDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.PurchaseOrderReceiveResultDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.dto.GoodsReceiptDTO;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.mapper.PurchaseOrderMapper;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.POStatus;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrder;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrderDetail;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.GoodsReceipt;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.GoodsReceiptLine;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository.PurchaseOrderRepository;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository.GoodsReceiptRepository;
import org.sspd.servicemgmt.purchaseoptions.service.PurchaseService;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
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
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SupplierRepository supplierRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderMapper mapper;
    private final PurchaseService purchaseService;
    private final CompanySettingsService companySettingsService;
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
        po.setStatus(POStatus.PENDING_APPROVAL);
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

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @Transactional(readOnly = true)
    public List<PurchaseOrderDTO> findLate() {
        return poRepository.findLateOpen(
                List.of(POStatus.OPEN, POStatus.PARTIAL, POStatus.APPROVED), LocalDate.now())
                .stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_READ')")
    @Transactional(readOnly = true)
    public List<GoodsReceiptDTO> findGoodsReceipts(Integer purchaseOrderId) {
        getEntity(purchaseOrderId);
        return goodsReceiptRepository.findByPurchaseOrderIdOrderByIdDesc(purchaseOrderId)
                .stream().map(this::toGoodsReceiptDto).toList();
    }

    /**
     * Header/detail edits allowed only while OPEN and nothing received yet.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_UPDATE')")
    @Transactional
    public PurchaseOrderDTO update(Integer id, PurchaseOrderDTO dto) {
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() != POStatus.PENDING_APPROVAL && po.getStatus() != POStatus.OPEN)
            throw new RuntimeException("Only purchase orders awaiting approval can be edited.");
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

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_DELETE') or hasAuthority('CAN_ACCESS_PURCHASE_ORDER_CANCEL_APPROVED')")
    @Transactional
    public void cancel(Integer id) {
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() == POStatus.CANCELLED)
            throw new IllegalStateException("Purchase order is already cancelled.");
        if (po.getStatus() == POStatus.RECEIVED)
            throw new IllegalStateException("Fully received order cannot be cancelled.");
        if (po.getStatus() == POStatus.REJECTED)
            throw new IllegalStateException("Rejected order cannot be cancelled.");
        if (po.getDetails().stream().anyMatch(d ->
                (d.getReceivedQty() != null && d.getReceivedQty() > 0)
                        || (d.getDamagedQty() != null && d.getDamagedQty() > 0)
                        || (d.getRejectedQty() != null && d.getRejectedQty() > 0)))
            throw new IllegalStateException("Partially received order cannot be cancelled. Use remaining receive/close flow instead.");
        if (po.getStatus() == POStatus.APPROVED) {
            if (!hasAuthority("CAN_ACCESS_PURCHASE_ORDER_CANCEL_APPROVED"))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Approved purchase order များကို ပယ်ဖျက်ရန် CAN_ACCESS_PURCHASE_ORDER_CANCEL_APPROVED ခွင့်ပြုချက် လိုအပ်သည်။");
        } else if (po.getStatus() == POStatus.PENDING_APPROVAL
                || po.getStatus() == POStatus.OPEN
                || po.getStatus() == POStatus.PENDING_FINAL_APPROVAL) {
            if (!hasAuthority("CAN_ACCESS_PURCHASE_ORDER_DELETE"))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Purchase order ပယ်ဖျက်ရန် CAN_ACCESS_PURCHASE_ORDER_DELETE ခွင့်ပြုချက် လိုအပ်သည်။");
        } else {
            throw new IllegalStateException("Only pending, open, or approved (not yet received) orders can be cancelled.");
        }
        po.setStatus(POStatus.CANCELLED);
        poRepository.save(po);
        messagingTemplate.convertAndSend(PO_TOPIC, "PO_CANCELLED");
    }

    /** Short-close remaining unreceived qty on PARTIAL (or APPROVED with nothing left to receive). */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_APPROVE')")
    @Transactional
    public PurchaseOrderDTO close(Integer id, String reason) {
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() == POStatus.CLOSED || po.getStatus() == POStatus.RECEIVED || po.getStatus() == POStatus.CANCELLED)
            throw new IllegalStateException("Purchase order cannot be closed in status " + po.getStatus());
        if (po.getStatus() != POStatus.PARTIAL && po.getStatus() != POStatus.APPROVED && po.getStatus() != POStatus.OPEN)
            throw new IllegalStateException("Only open/approved/partial orders can be short-closed.");
        boolean anyReceived = po.getDetails().stream().anyMatch(d ->
                (d.getReceivedQty() != null && d.getReceivedQty() > 0)
                        || (d.getDamagedQty() != null && d.getDamagedQty() > 0)
                        || (d.getRejectedQty() != null && d.getRejectedQty() > 0));
        if (!anyReceived && po.getStatus() != POStatus.APPROVED)
            throw new IllegalStateException("Nothing received yet — cancel the PO instead of closing.");
        if (reason != null && !reason.isBlank()) {
            String note = (po.getRemark() == null ? "" : po.getRemark() + "\n") + "Closed: " + reason.trim();
            po.setRemark(note.length() > 500 ? note.substring(0, 500) : note);
        }
        po.setStatus(POStatus.CLOSED);
        PurchaseOrder saved = poRepository.save(po);
        messagingTemplate.convertAndSend(PO_TOPIC, "PO_CLOSED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_APPROVE') or hasAuthority('CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE')")
    @Transactional
    public PurchaseOrderDTO approve(Integer id) {
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() == POStatus.PENDING_FINAL_APPROVAL) {
            if (!hasAuthority("CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE"))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Final approve အတွက် CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE ခွင့်ပြုချက် လိုအပ်သည်။");
            String firstApprover = po.getApprovedBy();
            if (firstApprover != null && firstApprover.equalsIgnoreCase(currentUsername()))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Same user cannot perform both first and final approval (segregation of duties).");
            po.setStatus(POStatus.APPROVED);
            po.setApprovedBy(currentUsername());
            po.setApprovedAt(LocalDateTime.now());
            po.setRejectedBy(null);
            po.setRejectedAt(null);
            po.setRejectionReason(null);
        } else if (po.getStatus() == POStatus.PENDING_APPROVAL || po.getStatus() == POStatus.OPEN) {
            if (!hasAuthority("CAN_ACCESS_PURCHASE_ORDER_APPROVE"))
                throw new org.springframework.security.access.AccessDeniedException(
                        "Approve အတွက် CAN_ACCESS_PURCHASE_ORDER_APPROVE ခွင့်ပြုချက် လိုအပ်သည်။");
            BigDecimal threshold = companySettingsService.getSettings().getPoFinalApprovalThreshold();
            BigDecimal total = po.getTotalAmount() == null ? BigDecimal.ZERO : po.getTotalAmount();
            if (threshold != null && threshold.signum() > 0 && total.compareTo(threshold) >= 0) {
                po.setStatus(POStatus.PENDING_FINAL_APPROVAL);
                // Stamp first approver for segregation-of-duties on final approve
                po.setApprovedBy(currentUsername());
                String note = (po.getRemark() == null ? "" : po.getRemark() + "\n")
                        + "First approved by " + currentUsername() + " — awaiting final approval";
                po.setRemark(note.length() > 500 ? note.substring(0, 500) : note);
            } else {
                po.setStatus(POStatus.APPROVED);
                po.setApprovedBy(currentUsername());
                po.setApprovedAt(LocalDateTime.now());
                po.setRejectedBy(null);
                po.setRejectedAt(null);
                po.setRejectionReason(null);
            }
        } else {
            throw new RuntimeException("Only pending purchase orders can be approved.");
        }
        PurchaseOrder saved = poRepository.save(po);
        messagingTemplate.convertAndSend(PO_TOPIC, "PO_APPROVED");
        return mapper.toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_APPROVE') or hasAuthority('CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE')")
    @Transactional
    public PurchaseOrderDTO reject(Integer id, String reason) {
        if (reason == null || reason.isBlank())
            throw new RuntimeException("Rejection reason is required.");
        PurchaseOrder po = getEntity(id);
        if (po.getStatus() != POStatus.PENDING_APPROVAL
                && po.getStatus() != POStatus.OPEN
                && po.getStatus() != POStatus.PENDING_FINAL_APPROVAL)
            throw new RuntimeException("Only pending purchase orders can be rejected.");
        if (po.getStatus() == POStatus.PENDING_FINAL_APPROVAL
                && !hasAuthority("CAN_ACCESS_PURCHASE_ORDER_FINAL_APPROVE")
                && !hasAuthority("CAN_ACCESS_PURCHASE_ORDER_APPROVE"))
            throw new org.springframework.security.access.AccessDeniedException("Reject permission required.");
        po.setStatus(POStatus.REJECTED);
        po.setRejectedBy(currentUsername());
        po.setRejectedAt(LocalDateTime.now());
        po.setRejectionReason(reason.trim());
        PurchaseOrder saved = poRepository.save(po);
        messagingTemplate.convertAndSend(PO_TOPIC, "PO_REJECTED");
        return mapper.toDto(saved);
    }

    /**
     * ✅ Goods Receipt — receives PO lines, creates the actual Purchase voucher
     * (stock + serials + accounting run inside PurchaseService.save) and updates
     * received quantities / PO status.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_ORDER_RECEIVE')")
    @Transactional
    public PurchaseOrderReceiveResultDTO receive(Integer id, PurchaseOrderReceiveDTO receive) {
        PurchaseOrder po = poRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Purchase Order not found: " + id));
        if (po.getStatus() != POStatus.APPROVED && po.getStatus() != POStatus.PARTIAL)
            throw new RuntimeException("Purchase Order must be approved before goods can be received.");

        Map<Integer, PurchaseOrderReceiveDTO.ReceiveLine> lineByDetailId = receive.getLines() == null
                ? Map.of()
                : receive.getLines().stream()
                    .filter(l -> l.getDetailId() != null)
                    .collect(Collectors.toMap(PurchaseOrderReceiveDTO.ReceiveLine::getDetailId, Function.identity(), (a, b) -> b));

        List<PurchaseOrderDetail> toReceive = po.getDetails().stream()
                .filter(d -> {
                    Integer orderedRemaining = safeInt(d.getQty()) - safeInt(d.getReceivedQty())
                            - safeInt(d.getDamagedQty()) - safeInt(d.getRejectedQty());
                    if (orderedRemaining <= 0) return false;
                    if (!lineByDetailId.isEmpty()) {
                        var line = lineByDetailId.get(d.getId());
                        return line != null && (safeInt(line.getQty()) + safeInt(line.getDamagedQty())
                                + safeInt(line.getRejectedQty())) > 0;
                    }
                    return true; // no explicit lines → receive everything remaining
                })
                .toList();

        if (toReceive.isEmpty())
            throw new RuntimeException("Nothing left to receive on this order.");

        PurchaseDTO purchaseDto = new PurchaseDTO();
        purchaseDto.setSupplierId(po.getSupplier().getId());
        purchaseDto.setStaffId(resolveReceiverStaff(receive.getStaffId()).getId());
        purchaseDto.setPurchaseDate(LocalDateTime.now());
        purchaseDto.setDueDate(receive.getDueDate());
        purchaseDto.setDiscountAmount(receive.getDiscountAmount());
        purchaseDto.setTaxAmount(receive.getTaxAmount());
        purchaseDto.setOtherCharges(receive.getOtherCharges());
        purchaseDto.setRemark(receive.getRemark() != null ? receive.getRemark()
                : ("Goods Receipt for PO " + po.getPoCode()));
        purchaseDto.setPoId(po.getId());
        purchaseDto.setSupplierInvoiceNo(receive.getSupplierInvoiceNo());
        purchaseDto.setPaymentMethodId(receive.getPaymentMethodId());
        purchaseDto.setTransactionNo(receive.getTransactionNo());
        purchaseDto.setPayments(receive.getPayments());

        List<PurchaseDetailDTO> details = new ArrayList<>();
        Map<Integer, Integer> acceptedByDetail = new java.util.HashMap<>();
        Map<Integer, Integer> damagedByDetail = new java.util.HashMap<>();
        Map<Integer, Integer> rejectedByDetail = new java.util.HashMap<>();
        Map<Integer, BigDecimal> invoiceCostByDetail = new java.util.HashMap<>();
        boolean hasVariance = false;
        for (PurchaseOrderDetail pod : toReceive) {
            var line = lineByDetailId.get(pod.getId());
            int qty = line != null && line.getQty() != null ? line.getQty()
                    : (safeInt(pod.getQty()) - safeInt(pod.getReceivedQty())
                    - safeInt(pod.getDamagedQty()) - safeInt(pod.getRejectedQty()));
            int damaged = line != null ? safeInt(line.getDamagedQty()) : 0;
            int rejected = line != null ? safeInt(line.getRejectedQty()) : 0;

            int alreadyAccounted = safeInt(pod.getReceivedQty()) + safeInt(pod.getDamagedQty()) + safeInt(pod.getRejectedQty());
            if (qty < 0 || damaged < 0 || rejected < 0 || alreadyAccounted + qty + damaged + rejected > safeInt(pod.getQty()))
                throw new RuntimeException("Accepted/damaged/rejected quantity exceeds ordered remaining for: "
                        + pod.getProduct().getName());
            BigDecimal invoiceCost = line != null && line.getInvoiceUnitCost() != null
                    ? line.getInvoiceUnitCost() : pod.getUnitCost();
            if (invoiceCost.compareTo(BigDecimal.ZERO) < 0)
                throw new RuntimeException("Invoice unit cost cannot be negative.");
            if (invoiceCost.compareTo(pod.getUnitCost()) != 0) hasVariance = true;
            acceptedByDetail.put(pod.getId(), qty);
            damagedByDetail.put(pod.getId(), damaged);
            rejectedByDetail.put(pod.getId(), rejected);
            invoiceCostByDetail.put(pod.getId(), invoiceCost);

            Product product = pod.getProduct();
            int bulkMonths = line != null && line.getWarrantyMonths() != null ? line.getWarrantyMonths()
                    : (product.getWarrantyMonths() != null ? product.getWarrantyMonths() : 0);
            List<Integer> itemWarranties = line != null && line.getItemWarranties() != null && !line.getItemWarranties().isEmpty()
                    ? line.getItemWarranties()
                    : java.util.stream.IntStream.range(0, qty).mapToObj(i -> bulkMonths).toList();

            if (qty > 0) details.add(PurchaseDetailDTO.builder()
                    .productId(product.getId())
                    .qty(qty)
                    .unitCost(invoiceCost)
                    .subtotal(invoiceCost.multiply(BigDecimal.valueOf(qty)))
                    .warrantyMonths(bulkMonths)
                    .itemWarranties(itemWarranties)
                    .serialNumbers(line != null && line.getSerialNumbers() != null ? line.getSerialNumbers() : null)
                    .serialConditions(line != null && line.getSerialConditions() != null ? line.getSerialConditions() : null)
                    .serialPhotos(line != null && line.getSerialPhotos() != null ? line.getSerialPhotos() : null)
                    .batchNumber(line != null ? line.getBatchNumber() : null)
                    .expiryDate(line != null ? line.getExpiryDate() : null)
                    .build());
        }
        if (hasVariance && (receive.getVarianceReason() == null || receive.getVarianceReason().isBlank()))
            throw new RuntimeException("Variance reason is required when invoice price differs from PO price.");
        PurchaseDTO createdPurchase = null;
        if (!details.isEmpty()) {
            purchaseDto.setDetails(details);
            createdPurchase = purchaseService.save(purchaseDto);
        }

        // Update received quantities + status
        for (PurchaseOrderDetail pod : toReceive) {
            int qty = acceptedByDetail.getOrDefault(pod.getId(), 0);
            pod.setReceivedQty(safeInt(pod.getReceivedQty()) + qty);
            pod.setDamagedQty(safeInt(pod.getDamagedQty()) + damagedByDetail.getOrDefault(pod.getId(), 0));
            pod.setRejectedQty(safeInt(pod.getRejectedQty()) + rejectedByDetail.getOrDefault(pod.getId(), 0));
        }
        boolean allReceived = po.getDetails().stream()
                .allMatch(d -> safeInt(d.getReceivedQty()) + safeInt(d.getDamagedQty())
                        + safeInt(d.getRejectedQty()) >= safeInt(d.getQty()));
        po.setStatus(allReceived ? POStatus.RECEIVED : POStatus.PARTIAL);
        PurchaseOrder savedPo = poRepository.save(po);

        messagingTemplate.convertAndSend(PO_TOPIC, "PO_RECEIVED");
        PurchaseDTO linked = createdPurchase;
        if (linked != null) {
            linked.setPoId(po.getId());
            linked.setPoCode(savedPo.getPoCode());
        }
        GoodsReceipt grn = GoodsReceipt.builder()
                .grnCode("PENDING")
                .purchaseOrder(savedPo)
                .purchaseId(linked != null ? linked.getId() : null)
                .supplierInvoiceNo(receive.getSupplierInvoiceNo())
                .receivedAt(LocalDateTime.now())
                .receivedBy(currentUsername())
                .matchStatus(hasVariance ? "VARIANCE" : "MATCHED")
                .varianceReason(receive.getVarianceReason())
                .build();
        List<GoodsReceiptLine> grnLines = new ArrayList<>();
        for (PurchaseOrderDetail pod : toReceive) {
            int accepted = acceptedByDetail.getOrDefault(pod.getId(), 0);
            BigDecimal invoiceCost = invoiceCostByDetail.getOrDefault(pod.getId(), pod.getUnitCost());
            grnLines.add(GoodsReceiptLine.builder().goodsReceipt(grn).poDetailId(pod.getId())
                    .productId(pod.getProduct().getId()).productName(pod.getProduct().getName())
                    .orderedQty(pod.getQty()).acceptedQty(accepted)
                    .damagedQty(damagedByDetail.getOrDefault(pod.getId(), 0))
                    .rejectedQty(rejectedByDetail.getOrDefault(pod.getId(), 0))
                    .poUnitCost(pod.getUnitCost()).invoiceUnitCost(invoiceCost)
                    .priceVariance(invoiceCost.subtract(pod.getUnitCost())).build());
        }
        grn.setLines(grnLines);
        grn = goodsReceiptRepository.save(grn);
        grn.setGrnCode(String.format("GRN-%06d", grn.getId()));
        grn = goodsReceiptRepository.save(grn);
        return PurchaseOrderReceiveResultDTO.builder()
                .order(mapper.toDto(savedPo))
                .purchase(linked)
                .goodsReceipt(toGoodsReceiptDto(grn))
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────

    private PurchaseOrder getEntity(Integer id) {
        return poRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found: " + id));
    }

    private String currentUsername() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "SYSTEM";
    }

    private Staff resolveReceiverStaff(Integer requestedStaffId) {
        if (hasAuthority("CAN_ACCESS_PURCHASE_STAFF_OVERRIDE")
                && requestedStaffId != null && requestedStaffId > 0) {
            Staff requested = staffRepository.findById(requestedStaffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Receiver staff not found"));
            if (!requested.isActive()) throw new IllegalStateException("Receiver staff is inactive.");
            return requested;
        }
        String username = currentUsername();
        var user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Authenticated receiver user is not linked to a staff record."));
        Staff receiver = user.getStaff();
        if (receiver == null || !receiver.isActive())
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated receiver must be linked to an active staff record.");
        return receiver;
    }

    private boolean hasAuthority(String authority) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private String generatePoCode(Integer id) {
        var cfg = companySettingsService.getSettings();
        String prefix = cfg.getPoPrefix() != null && !cfg.getPoPrefix().isBlank() ? cfg.getPoPrefix() : "PO";
        int digits = cfg.getPoDigits() != null ? cfg.getPoDigits() : 5;
        return String.format("%s-%0" + digits + "d", prefix, id);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private GoodsReceiptDTO toGoodsReceiptDto(GoodsReceipt grn) {
        return GoodsReceiptDTO.builder().id(grn.getId()).grnCode(grn.getGrnCode())
                .purchaseOrderId(grn.getPurchaseOrder().getId()).poCode(grn.getPurchaseOrder().getPoCode())
                .purchaseId(grn.getPurchaseId()).supplierInvoiceNo(grn.getSupplierInvoiceNo())
                .receivedAt(grn.getReceivedAt()).receivedBy(grn.getReceivedBy())
                .matchStatus(grn.getMatchStatus()).varianceReason(grn.getVarianceReason())
                .lines(grn.getLines().stream().map(line -> GoodsReceiptDTO.Line.builder()
                        .productId(line.getProductId()).productName(line.getProductName())
                        .orderedQty(line.getOrderedQty()).acceptedQty(line.getAcceptedQty())
                        .damagedQty(line.getDamagedQty()).rejectedQty(line.getRejectedQty())
                        .poUnitCost(line.getPoUnitCost()).invoiceUnitCost(line.getInvoiceUnitCost())
                        .priceVariance(line.getPriceVariance()).build()).toList()).build();
    }
}
