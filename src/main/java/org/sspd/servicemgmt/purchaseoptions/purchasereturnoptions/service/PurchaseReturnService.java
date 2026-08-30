package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnActivityDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.dto.PurchaseReturnAttachmentDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturnActivity;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturnAttachment;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnActivityRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnAttachmentRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.mapper.PurchaseReturnMapper;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.model.PurchaseReturnReason;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.repository.PurchaseReturnReasonRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetailWarranty;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.dto.PurchaseReturnDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.sspd.servicemgmt.api.PageResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PurchaseReturnService {

    private final PurchaseReturnRepository purchaseReturnRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockMovementService stockMovementService;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final JournalWriter journalWriter;
    private final AccountResolver accountResolver;
    private final PurchaseReturnMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProductSerialRepository productSerialRepository;
    private final org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService companySettingsService;
    private final org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard periodGuard;
    private final org.sspd.servicemgmt.stockoptions.lotoptions.service.StockLotService stockLotService;
    private final CashDrawerService cashDrawerService;
    private final PurchaseReturnReasonRepository returnReasonRepository;
    private final PurchaseReturnActivityRepository activityRepository;
    private final PurchaseReturnAttachmentRepository attachmentRepository;

    private static final String PURCHASE_RETURN_TOPIC = "/topic/purchase-return";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING = "PENDING_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_DISPATCHED = "DISPATCHED";
    private static final String STATUS_RECEIVED = "SUPPLIER_RECEIVED";
    private static final String STATUS_SETTLED = "SETTLED";
    private static final String STATUS_VOIDED = "VOIDED";

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_CREATE')")
    @Transactional
    public PurchaseReturnDTO save(PurchaseReturnDTO dto) {
        periodGuard.assertOpen(dto.getReturnDate(), "create purchase return");
        if (dto.getDetails() == null || dto.getDetails().isEmpty()) {
            throw new RuntimeException("Purchase return details are required");
        }

        if (dto.getPurchaseId() == null) {
            throw new RuntimeException("Purchase reference is required for purchase return");
        }

        Purchase purchase = purchaseRepository.findById(dto.getPurchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (!purchase.isEffectivelyConfirmed()) {
            if (purchase.isCancelled()) {
                throw new RuntimeException("Cannot create a purchase return for a cancelled voucher. Cancellation already reversed stock and journals.");
            }
            throw new RuntimeException("Cannot create a purchase return for a draft voucher. Confirm the purchase first.");
        }
        Supplier supplier = purchase.getSupplier();

        if (dto.getReturnDate() != null && purchase.getPurchaseDate() != null
                && dto.getReturnDate().isBefore(purchase.getPurchaseDate())) {
            throw new RuntimeException("Return date cannot be before purchase date");
        }

        if (dto.getReason() == null || dto.getReason().isBlank()) {
            throw new RuntimeException("Return reason is required");
        }

        PurchaseReturn entity = mapper.toEntity(dto);
        applyNotNullDefaults(entity);
        entity.setReturnNo(generateReturnNo());
        entity.setPurchase(purchase);
        entity.setStatus(STATUS_DRAFT);

        if (entity.getReturnDate() == null) {
            entity.setReturnDate(LocalDateTime.now());
        }

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseReturnDetail> detailEntities = new ArrayList<>();

        for (PurchaseReturnDetailDTO dDto : dto.getDetails()) {
            Product product = productRepository.findById(dDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            int qty = dDto.getQty() != null ? dDto.getQty() : 0;
            if (qty <= 0) {
                throw new RuntimeException("Return qty must be greater than zero for product: " + product.getName());
            }
            int purchasedQty = purchasedQty(purchase, product.getId());
            if (purchasedQty <= 0) {
                throw new RuntimeException("Product does not belong to selected purchase: " + product.getName());
            }
            int alreadyReturned = returnedQty(purchase.getId(), product.getId(), null);
            int returnableQty = purchasedQty - alreadyReturned;
            if (qty > returnableQty) {
                throw new RuntimeException("Return qty exceeds returnable qty for product: " + product.getName()
                        + ". Returnable qty: " + returnableQty);
            }

            List<String> serials = normalizeSerials(dDto.getSerialNumbers());
            Set<String> purchasedSerials = purchasedSerials(purchase, product.getId());
            boolean serialTracked = !purchasedSerials.isEmpty() || Boolean.TRUE.equals(product.getHasSerial());

            if (serialTracked) {
                if (serials.size() != qty) {
                    throw new RuntimeException("Serial count must match qty for product: " + product.getName());
                }
                for (String sn : serials) {
                    if (!purchasedSerials.contains(sn.toUpperCase())) {
                        throw new RuntimeException("Serial number '" + sn + "' was not purchased on this voucher");
                    }
                    if (isSerialAlreadyReturned(purchase.getId(), product.getId(), sn, null)) {
                        throw new RuntimeException("Serial number '" + sn + "' was already returned");
                    }
                    ProductSerial serial = productSerialRepository.findBySerialNumber(sn)
                            .orElseThrow(() -> new RuntimeException("Serial number '" + sn + "' not found in inventory"));
                    if (!serial.getProduct().getId().equals(product.getId())) {
                        throw new RuntimeException("Serial number '" + sn + "' does not belong to product: " + product.getName());
                    }
                    if (serial.getStatus() != SerialStatus.Available) {
                        throw new RuntimeException("Serial number '" + sn + "' is not available for return");
                    }
                }
            } else {
                int current = product.getStockQty() != null ? product.getStockQty() : 0;
                if (current < qty) {
                    throw new RuntimeException("Available stock is not enough for return: " + product.getName()
                            + ". Available qty: " + current);
                }
                serials = List.of();
            }

            BigDecimal returnUnitPrice = discountedUnitCost(purchase, product.getId());
            if (returnUnitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Return unit price cannot be resolved for product: " + product.getName());
            }
            BigDecimal subtotal = returnUnitPrice.multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);

            PurchaseReturnReason detailReason = resolveReason(dDto.getReasonId());
            PurchaseReturnDetail detail = PurchaseReturnDetail.builder()
                    .purchaseReturn(entity)
                    .product(product)
                    .qty(qty)
                    .unitPrice(returnUnitPrice)
                    .subtotal(subtotal)
                    .allocatedShippingCost(safe(dDto.getAllocatedShippingCost()))
                    .serialNumber(joinSerials(serials))
                    .reason(detailReason)
                    .quarantinedQty(0)
                    .dispatchedQty(0)
                    .build();
            detailEntities.add(detail);
        }

        BigDecimal previousReturns = safe(purchase.getReturnAmount());
        BigDecimal purchaseTotal = originalPurchaseNet(purchase);
        BigDecimal paidAmount = safe(purchase.getPaidAmount());
        BigDecimal netAfterThisReturn = purchaseTotal.subtract(previousReturns.add(total));
        if (netAfterThisReturn.compareTo(BigDecimal.ZERO) < 0) {
            netAfterThisReturn = BigDecimal.ZERO;
        }

        BigDecimal creditBeforeRefund = paidAmount.subtract(netAfterThisReturn);
        if (creditBeforeRefund.compareTo(BigDecimal.ZERO) < 0) {
            creditBeforeRefund = BigDecimal.ZERO;
        }

        BigDecimal refundAmount = paymentTotal(dto.getPayments(), dto.getRefundAmount());
        if (refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Refund amount cannot be negative");
        }
        if (refundAmount.compareTo(creditBeforeRefund) > 0) {
            throw new RuntimeException("Refund amount exceeds supplier credit. Max refund: " + creditBeforeRefund);
        }

        entity.setDetails(detailEntities);
        entity.setTotalReturnAmount(total);
        configureShipping(entity, dto);
        // Financial settlement is deliberately deferred until SUPPLIER_RECEIVED.
        entity.setRefundAmount(BigDecimal.ZERO);

        PurchaseReturn savedEntity = purchaseReturnRepository.save(entity);
        recordActivity(savedEntity, "CREATED", null, savedEntity.getStatus(), savedEntity.getReason());

        messagingTemplate.convertAndSend(PURCHASE_RETURN_TOPIC, "PURCHASE_RETURN_CREATED");
        return toDto(savedEntity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_READ')")
    @Transactional(readOnly = true)
    public PageResponse<PurchaseReturnDTO> findAll(String search,LocalDateTime from,LocalDateTime to,Integer supplierId,Integer purchaseId,String status,String settlementType,String resolutionType,int page,int size) {
        return PageResponse.of(
                purchaseReturnRepository.findFiltered(search,from,to,supplierId,purchaseId,status,settlementType,resolutionType,PageRequest.of(page, size, Sort.by("id").descending()))
                        .map(this::toDto)
        );
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_READ')")
    @Transactional(readOnly = true)
    public List<PurchaseReturnDTO> findByPurchaseId(Integer purchaseId) {
        return purchaseReturnRepository.findByPurchaseId(purchaseId).stream()
                .map(this::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_READ')")
    @Transactional(readOnly = true)
    public PurchaseReturnDTO findById(Integer id) {
        PurchaseReturn entity = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found with id: " + id));
        return toDto(entity);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_SUBMIT')")
    @Transactional
    public PurchaseReturnDTO submit(Integer id) {
        PurchaseReturn entity = getReturn(id);
        if (STATUS_PENDING.equals(entity.getStatus())) return toDto(entity);
        requireStatus(entity, STATUS_DRAFT);
        entity.setStatus(STATUS_PENDING);
        entity.setSubmittedBy(currentActor());
        entity.setSubmittedAt(LocalDateTime.now());
        return workflowSaved(entity, "PURCHASE_RETURN_SUBMITTED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_APPROVE')")
    @Transactional
    public PurchaseReturnDTO approve(Integer id, PurchaseReturnDTO request) {
        PurchaseReturn entity = getReturn(id);
        if (STATUS_APPROVED.equals(entity.getStatus())) return toDto(entity);
        requireStatus(entity, STATUS_PENDING);
        for (PurchaseReturnDetail detail : entity.getDetails()) {
            Product product = detail.getProduct();
            int qty = detail.getQty() == null ? 0 : detail.getQty();
            List<String> serials = normalizeSerials(detail.getSerialNumber() == null ? List.of() : List.of(detail.getSerialNumber()));
            if (serials.isEmpty()) {
                int current = product.getStockQty() == null ? 0 : product.getStockQty();
                int quarantined = product.getQuarantinedQty() == null ? 0 : product.getQuarantinedQty();
                if (current - quarantined < qty) throw new IllegalStateException("Insufficient stock to quarantine: " + product.getName());
                product.setQuarantinedQty(quarantined + qty);
                productRepository.save(product);
            } else {
                for (String sn : serials) {
                    ProductSerial serial = productSerialRepository.findBySerialNumber(sn)
                            .orElseThrow(() -> new IllegalStateException("Serial not found: " + sn));
                    if (serial.getStatus() != SerialStatus.Available)
                        throw new IllegalStateException("Serial is not available to quarantine: " + sn);
                    serial.setStatus(SerialStatus.Quarantined);
                    productSerialRepository.save(serial);
                }
            }
            detail.setQuarantinedQty(qty);
        }
        entity.setStatus(STATUS_APPROVED);
        entity.setApprovedBy(currentActor());
        entity.setApprovedAt(LocalDateTime.now());
        entity.setApprovalNote(request == null ? null : request.getApprovalNote());
        return workflowSaved(entity, "PURCHASE_RETURN_APPROVED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_APPROVE')")
    @Transactional
    public PurchaseReturnDTO reject(Integer id, PurchaseReturnDTO request) {
        PurchaseReturn entity = getReturn(id);
        requireStatus(entity, STATUS_PENDING);
        if (request == null || request.getRejectionReason() == null || request.getRejectionReason().isBlank())
            throw new IllegalArgumentException("Rejection reason is required");
        entity.setRejectedBy(currentActor());
        entity.setRejectedAt(LocalDateTime.now());
        entity.setRejectionReason(request.getRejectionReason().trim());
        entity.setStatus(STATUS_DRAFT);
        return workflowSaved(entity, "PURCHASE_RETURN_REJECTED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DISPATCH')")
    @Transactional
    public PurchaseReturnDTO dispatch(Integer id, PurchaseReturnDTO request) {
        PurchaseReturn entity = getReturn(id);
        if (STATUS_DISPATCHED.equals(entity.getStatus())) return toDto(entity);
        requireStatus(entity, STATUS_APPROVED);
        if (request == null || request.getCarrier() == null || request.getCarrier().isBlank()
                || request.getTrackingNo() == null || request.getTrackingNo().isBlank())
            throw new IllegalArgumentException("Carrier and tracking number are required");
        Purchase purchase = entity.getPurchase();
        for (PurchaseReturnDetail detail : entity.getDetails()) {
            Product product = detail.getProduct();
            int qty = detail.getQty();
            if (!Integer.valueOf(qty).equals(detail.getQuarantinedQty()))
                throw new IllegalStateException("Return detail is not fully quarantined");
            List<String> serials = normalizeSerials(detail.getSerialNumber() == null ? List.of() : List.of(detail.getSerialNumber()));
            if (serials.isEmpty()) {
                int current = product.getStockQty() == null ? 0 : product.getStockQty();
                if (current < qty) throw new IllegalStateException("Insufficient quarantined stock: " + product.getName());
                product.setStockQty(current - qty);
                product.setQuarantinedQty(Math.max(0, (product.getQuarantinedQty() == null ? 0 : product.getQuarantinedQty()) - qty));
                productRepository.save(product);
                stockLotService.consumePurchaseReturn(purchase, product, qty);
            } else {
                for (String sn : serials) {
                    ProductSerial serial = productSerialRepository.findBySerialNumber(sn)
                            .orElseThrow(() -> new IllegalStateException("Serial not found: " + sn));
                    if (serial.getStatus() != SerialStatus.Quarantined)
                        throw new IllegalStateException("Serial is not quarantined: " + sn);
                    serial.setStatus(SerialStatus.Returned_To_Supplier);
                    productSerialRepository.save(serial);
                }
            }
            detail.setDispatchedQty(qty);
            stockMovementService.recordMovement(StockMovement.builder().product(product)
                    .movementType(MovementType.OUT).qty(qty).referenceId(entity.getId())
                    .referenceType("PurchaseReturnDispatch").build());
        }
        entity.setCarrier(request.getCarrier().trim());
        entity.setTrackingNo(request.getTrackingNo().trim());
        entity.setDispatchedAt(request.getDispatchedAt() == null ? LocalDateTime.now() : request.getDispatchedAt());
        entity.setDeliveryProof(request.getDeliveryProof());
        postCompanyShipping(entity);
        entity.setStatus(STATUS_DISPATCHED);
        return workflowSaved(entity, "PURCHASE_RETURN_DISPATCHED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_RECEIVE')")
    @Transactional
    public PurchaseReturnDTO supplierReceived(Integer id, PurchaseReturnDTO request) {
        PurchaseReturn entity = getReturn(id);
        if (STATUS_RECEIVED.equals(entity.getStatus())) return toDto(entity);
        requireStatus(entity, STATUS_DISPATCHED);
        entity.setSupplierReceivedAt(request != null && request.getSupplierReceivedAt() != null
                ? request.getSupplierReceivedAt() : LocalDateTime.now());
        if (request != null && request.getDeliveryProof() != null) entity.setDeliveryProof(request.getDeliveryProof());
        entity.setStatus(STATUS_RECEIVED);
        return workflowSaved(entity, "PURCHASE_RETURN_SUPPLIER_RECEIVED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_SETTLE')")
    @Transactional
    public PurchaseReturnDTO settle(Integer id, PurchaseReturnDTO request) {
        PurchaseReturn entity = getReturn(id);
        if (STATUS_SETTLED.equals(entity.getStatus())) return toDto(entity);
        requireStatus(entity, STATUS_RECEIVED);
        if (request == null) throw new IllegalArgumentException("Settlement details are required");
        String type = normalizeSettlementType(request.getSettlementType());
        BigDecimal expected = settlementValue(entity);
        if (request.getExpectedCreditAmount() != null
                && safe(request.getExpectedCreditAmount()).compareTo(expected) != 0)
            throw new IllegalArgumentException("Expected supplier credit must equal return value less supplier shipping responsibility: " + expected);
        BigDecimal noteAmount = safe(request.getSupplierCreditNoteAmount());
        boolean creditNoteSettlement = Set.of("CREDIT_NOTE", "OFFSET", "SPLIT").contains(type);
        BigDecimal variance = creditNoteSettlement ? noteAmount.subtract(expected) : BigDecimal.ZERO;
        if (creditNoteSettlement && variance.signum() != 0
                && (request.getCreditVarianceReason() == null || request.getCreditVarianceReason().isBlank()))
            throw new IllegalArgumentException("Credit note variance reason is required");
        if (creditNoteSettlement
                && (request.getSupplierCreditNoteNo() == null || request.getSupplierCreditNoteNo().isBlank()))
            throw new IllegalArgumentException("Supplier credit note number is required");

        Purchase purchase = entity.getPurchase();
        Supplier supplier = purchase.getSupplier();
        BigDecimal oldDue = safe(purchase.getDueAmount());
        BigDecimal oldCredit = safe(purchase.getSupplierCreditAmount());
        BigDecimal refund = ("REFUND".equals(type) || "SPLIT".equals(type))
                ? paymentTotal(request.getPayments(), request.getRefundAmount()) : BigDecimal.ZERO;
        if (refund.compareTo(expected) > 0)
            throw new IllegalArgumentException("Refund amount exceeds expected supplier settlement: " + expected);
        entity.setRefundAmount(refund);
        entity.setSettlementType(type);
        entity.setExpectedCreditAmount(expected);
        entity.setSupplierCreditNoteNo(request.getSupplierCreditNoteNo());
        entity.setSupplierCreditNoteAmount(noteAmount);
        entity.setCreditVariance(variance);
        entity.setCreditVarianceReason(request.getCreditVarianceReason());
        entity.setSettlementReference(request.getSettlementReference());
        entity.setSettledAt(LocalDateTime.now());
        entity.setStatus(STATUS_SETTLED);
        purchaseReturnRepository.save(entity);

        recalculatePurchaseFinancials(purchase);
        BigDecimal creditIncrease = safe(purchase.getSupplierCreditAmount()).subtract(oldCredit).max(BigDecimal.ZERO);
        PaymentMethod refundMethod = null;
        if (refund.signum() > 0) {
            Integer methodId = request.getPaymentMethodId() != null ? request.getPaymentMethodId()
                    : request.getPayments() != null && !request.getPayments().isEmpty()
                    ? request.getPayments().get(0).getPaymentMethodId() : null;
            if (methodId == null) throw new IllegalArgumentException("Payment method is required for refund");
            refundMethod = paymentMethodRepository.findById(methodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            recordPaymentTransactions(entity, refund, request.getTransactionNo(), refundMethod, request.getPayments());
        }
        Integer staffId = purchase.getStaff() == null ? null : purchase.getStaff().getId();
        createReturnJournal(entity, refundMethod, refund, oldDue.min(expected),
                creditIncrease, staffId, supplier == null ? "" : supplier.getName(), request.getPayments(),
                safe(entity.getSupplierShippingPortion()));
        if (supplier != null) syncSupplierBalance(supplier);
        return workflowSaved(entity, "PURCHASE_RETURN_SETTLED");
    }



    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_UPDATE')")
    @Transactional
    public PurchaseReturnDTO update(Integer id, PurchaseReturnDTO dto) {
        PurchaseReturn existing = getReturn(id);
        requireStatus(existing, STATUS_DRAFT);
        if (dto.getVersion() != null && !dto.getVersion().equals(existing.getVersion()))
            throw new jakarta.persistence.OptimisticLockException("Purchase return was changed by another user");
        if (dto.getReturnDate() != null) existing.setReturnDate(dto.getReturnDate());
        if (dto.getReason() != null && !dto.getReason().isBlank()) existing.setReason(dto.getReason().trim());
        existing.setResolutionType(trimToNull(dto.getResolutionType()));
        existing.setRmaNumber(trimToNull(dto.getRmaNumber()));
        existing.setClaimDate(dto.getClaimDate());
        existing.setExpectedResolutionDate(dto.getExpectedResolutionDate());
        existing.setSupplierContact(trimToNull(dto.getSupplierContact()));
        existing.setClaimStatus(trimToNull(dto.getClaimStatus()));
        existing.setReplacementExpectedQty(dto.getReplacementExpectedQty());
        existing.setReplacementReceivedQty(dto.getReplacementReceivedQty());
        existing.setGoodsReceiptId(dto.getGoodsReceiptId());
        if (dto.getDetails() != null && dto.getDetails().size() == existing.getDetails().size()) {
            for (int i = 0; i < dto.getDetails().size(); i++) {
                PurchaseReturnDetailDTO incoming = dto.getDetails().get(i);
                PurchaseReturnDetail detail = existing.getDetails().get(i);
                if (incoming.getReasonId() != null) detail.setReason(resolveReason(incoming.getReasonId()));
                detail.setAllocatedShippingCost(safe(incoming.getAllocatedShippingCost()));
            }
        }
        configureShipping(existing, dto);
        return workflowSaved(existing, "PURCHASE_RETURN_UPDATED");
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_UPDATE')")
    @Transactional
    public PurchaseReturnDTO voidReturn(Integer id, PurchaseReturnDTO dto) {
        periodGuard.assertOpen(LocalDateTime.now(), "void purchase return");
        PurchaseReturn existing = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found with id: " + id));
        if (STATUS_VOIDED.equalsIgnoreCase(existing.getStatus())) {
            throw new RuntimeException("Purchase return is already voided.");
        }
        if (STATUS_DISPATCHED.equals(existing.getStatus()) || STATUS_RECEIVED.equals(existing.getStatus())) {
            throw new IllegalStateException("A dispatched return cannot be voided before settlement; record supplier receipt/settlement or a controlled reversal.");
        }
        String reason = dto != null ? dto.getVoidReason() : null;
        if (reason == null || reason.isBlank()) {
            reason = dto != null ? dto.getReason() : null;
        }
        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("Void reason is required");
        }

        boolean posted = STATUS_SETTLED.equals(existing.getStatus());
        Purchase purchase = existing.getPurchase();
        Supplier supplier = purchase != null ? purchase.getSupplier() : null;
        BigDecimal dueBeforeVoid = purchase != null ? safe(purchase.getDueAmount()) : BigDecimal.ZERO;
        BigDecimal creditBeforeVoid = purchase != null ? safe(purchase.getSupplierCreditAmount()) : BigDecimal.ZERO;
        List<PaymentTransaction> refundTransactions = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(existing.getId(), ReferenceType.Purchase_Return)
                .stream().filter(tx -> !Boolean.TRUE.equals(tx.getReversed())).toList();

        for (PurchaseReturnDetail detail : existing.getDetails()) {
            Product product = detail.getProduct();
            int qty = detail.getQty() != null ? detail.getQty() : 0;
            List<String> serials = normalizeSerials(detail.getSerialNumber() == null ? List.of() : List.of(detail.getSerialNumber()));

            if (!posted) {
                if (serials.isEmpty()) {
                    product.setQuarantinedQty(Math.max(0,
                            (product.getQuarantinedQty() == null ? 0 : product.getQuarantinedQty())
                                    - (detail.getQuarantinedQty() == null ? 0 : detail.getQuarantinedQty())));
                    productRepository.save(product);
                }
                for (String sn : serials) {
                    productSerialRepository.findBySerialNumber(sn).ifPresent(serial -> {
                        if (serial.getStatus() == SerialStatus.Quarantined) {
                            serial.setStatus(SerialStatus.Available);
                            productSerialRepository.save(serial);
                        }
                    });
                }
                detail.setQuarantinedQty(0);
                continue;
            }
            if (!serials.isEmpty()) {
                for (String sn : serials) {
                    var tracked = productSerialRepository.findBySerialNumber(sn);
                    if (tracked.isPresent()) {
                        if (tracked.get().getStatus() != SerialStatus.Returned_To_Supplier) {
                            throw new RuntimeException("Cannot void return. Serial is already active in inventory: " + sn);
                        }
                        tracked.get().setStatus(SerialStatus.Available);
                        productSerialRepository.save(tracked.get());
                        continue;
                    }
                    PurchaseDetailWarranty warranty = findPurchaseWarranty(purchase, product.getId(), sn);
                    productSerialRepository.save(ProductSerial.builder()
                            .product(product)
                            .warehouse(purchase.getWarehouse() != null ? purchase.getWarehouse() : product.getWarehouse())
                            .serialNumber(sn)
                            .status(SerialStatus.Available)
                            .warrantyMonths(warranty != null ? warranty.getWarrantyMonths() : null)
                            .warrantyStartDate(warranty != null ? warranty.getWarrantyStartDate() : null)
                            .warrantyEndDate(warranty != null ? warranty.getWarrantyEndDate() : null)
                            .build());
                }
            } else {
                int current = product.getStockQty() != null ? product.getStockQty() : 0;
                product.setStockQty(current + qty);
                productRepository.save(product);
                if (purchase != null) stockLotService.restorePurchaseReturn(purchase, product, qty);
            }

            stockMovementService.recordMovement(StockMovement.builder()
                    .product(product)
                    .movementType(MovementType.IN)
                    .qty(qty)
                    .referenceId(existing.getId())
                    .referenceType("PurchaseReturnVoid")
                    .build());
        }

        existing.setStatus(STATUS_VOIDED);
        existing.setVoidedAt(LocalDateTime.now());
        existing.setVoidReason(reason);
        PurchaseReturn saved = purchaseReturnRepository.save(existing);

        if (purchase != null && posted) {
            recalculatePurchaseFinancials(purchase);
            BigDecimal payableReversal = safe(purchase.getDueAmount()).subtract(dueBeforeVoid).max(BigDecimal.ZERO);
            BigDecimal supplierCreditReversal = creditBeforeVoid
                    .subtract(safe(purchase.getSupplierCreditAmount())).max(BigDecimal.ZERO);
            BigDecimal refundReversal = refundTransactions.stream()
                    .map(PaymentTransaction::getAmount).map(this::safe)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal accounted = payableReversal.add(supplierCreditReversal).add(refundReversal);
            if (accounted.compareTo(settlementValue(existing)) != 0) {
                throw new IllegalStateException(
                        "Cannot void return because its supplier credit was already consumed or balances changed. Reverse credit applications first.");
            }
            reverseRefundTransactions(existing, refundTransactions, reason);
            if (supplier != null) syncSupplierBalance(supplier);
            Integer staffId = purchase.getStaff() != null ? purchase.getStaff().getId() : null;
            createVoidJournal(saved, staffId, supplier != null ? supplier.getName() : "",
                    payableReversal, supplierCreditReversal, refundTransactions);
        }

        messagingTemplate.convertAndSend(PURCHASE_RETURN_TOPIC, "PURCHASE_RETURN_VOIDED");
        return toDto(saved);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_RETURN_DELETE')")
    @Transactional
    public void delete(Integer id) {
        throw new RuntimeException("Confirmed purchase return cannot be deleted. Create a reversal/void workflow instead.");
    }

    private PurchaseReturnDTO toDto(PurchaseReturn entity) {
        PurchaseReturnDTO dto = mapper.toDto(entity);
        dto.setActivities(activityRepository.findByPurchaseReturnIdOrderByOccurredAtAsc(entity.getId()).stream().map(a -> { PurchaseReturnActivityDTO d=new PurchaseReturnActivityDTO(); d.setId(a.getId()); d.setEventType(a.getEventType()); d.setFromStatus(a.getFromStatus()); d.setToStatus(a.getToStatus()); d.setNote(a.getNote()); d.setActor(a.getActor()); d.setOccurredAt(a.getOccurredAt()); return d; }).toList());
        dto.setAttachments(attachmentRepository.findByPurchaseReturnIdOrderByUploadedAtDesc(entity.getId()).stream().map(a -> { PurchaseReturnAttachmentDTO d=new PurchaseReturnAttachmentDTO(); d.setId(a.getId()); d.setAttachmentType(a.getAttachmentType()); d.setFileName(a.getFileName()); d.setContentType(a.getContentType()); d.setDataUrl(a.getDataUrl()); d.setUploadedBy(a.getUploadedBy()); d.setUploadedAt(a.getUploadedAt()); return d; }).toList());
        Purchase purchase = entity.getPurchase();
        if (purchase != null) {
            dto.setPurchaseCode(purchase.getPurchaseCode());
            if (purchase.getSupplier() != null) {
                dto.setSupplierName(purchase.getSupplier().getName());
            }
        }
        List<PaymentTransaction> payments = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(entity.getId(), ReferenceType.Purchase_Return);
        dto.setPayments(payments.stream().map(this::paymentToDto).toList());
        if (!payments.isEmpty() && payments.get(0).getPaymentMethod() != null) {
            dto.setPaymentMethodId(payments.get(0).getPaymentMethod().getId());
            dto.setPaymentMethodName(payments.get(0).getPaymentMethod().getMethodName());
            dto.setTransactionNo(payments.get(0).getTransactionNo());
        }
        List<PaymentTransaction> shippingPayments = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(entity.getId(), ReferenceType.Purchase_Return_Shipping);
        if (!shippingPayments.isEmpty()) {
            PaymentTransaction shipping = shippingPayments.get(0);
            dto.setShippingPaymentTransaction(paymentToDto(shipping));
            if (shipping.getPaymentMethod() != null) {
                dto.setShippingPaymentMethodId(shipping.getPaymentMethod().getId());
                dto.setShippingPaymentMethodName(shipping.getPaymentMethod().getMethodName());
            }
        } else if (entity.getShippingPaymentMethodId() != null) {
            paymentMethodRepository.findById(entity.getShippingPaymentMethodId()).ifPresent(method -> {
                dto.setShippingPaymentMethodId(method.getId());
                dto.setShippingPaymentMethodName(method.getMethodName());
            });
        }
        return dto;
    }

    private PaymentTransactionDTO paymentToDto(PaymentTransaction payment) {
        PaymentTransactionDTO dto = new PaymentTransactionDTO();
        dto.setId(payment.getId());
        dto.setReferenceId(payment.getReferenceId());
        dto.setReferenceType(payment.getReferenceType() != null ? payment.getReferenceType().name() : null);
        if (payment.getPaymentMethod() != null) {
            dto.setPaymentMethodId(payment.getPaymentMethod().getId());
            dto.setPaymentMethodName(payment.getPaymentMethod().getMethodName());
        }
        dto.setAmount(payment.getAmount());
        dto.setTransactionNo(payment.getTransactionNo());
        dto.setPaymentDate(payment.getPaymentDate());
        return dto;
    }

    private void syncSupplierBalance(Supplier supplier) {
        BigDecimal totalDue = purchaseRepository.sumDueAmountBySupplierId(supplier.getId());
        if (totalDue == null) totalDue = BigDecimal.ZERO;
        BigDecimal supplierCredit = purchaseRepository.sumSupplierCreditAmountBySupplierId(supplier.getId());
        if (supplierCredit == null) supplierCredit = BigDecimal.ZERO;
        BigDecimal opening = supplier.getOpeningBalance() != null ? supplier.getOpeningBalance() : BigDecimal.ZERO;
        supplier.setCurrentBalance(opening.add(totalDue).subtract(supplierCredit)
                .subtract(safe(supplier.getAdvanceBalance())));
        supplierRepository.save(supplier);
    }

    private void recalculatePurchaseFinancials(Purchase purchase) {
        BigDecimal returnAmount = purchaseReturnRepository.findByPurchaseId(purchase.getId()).stream()
                .filter(this::isPosted)
                .map(this::settlementValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount = purchaseReturnRepository.findByPurchaseId(purchase.getId()).stream()
                .filter(this::isPosted)
                .map(r -> safe(r.getRefundAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = originalPurchaseNet(purchase);
        BigDecimal paid = safe(purchase.getPaidAmount());
        BigDecimal net = total.subtract(returnAmount);
        if (net.compareTo(BigDecimal.ZERO) < 0) net = BigDecimal.ZERO;

        BigDecimal due = net.subtract(paid);
        if (due.compareTo(BigDecimal.ZERO) < 0) due = BigDecimal.ZERO;

        BigDecimal supplierCredit = paid.subtract(net).subtract(refundedAmount);
        if (supplierCredit.compareTo(BigDecimal.ZERO) < 0) supplierCredit = BigDecimal.ZERO;

        purchase.setReturnAmount(returnAmount);
        purchase.setRefundAmount(refundedAmount);
        purchase.setNetAmount(net);
        purchase.setDueAmount(due);
        purchase.setSupplierCreditAmount(supplierCredit);

        if (due.compareTo(BigDecimal.ZERO) <= 0) {
            purchase.setPaymentStatus(PaymentStatus.Paid);
        } else if (paid.compareTo(BigDecimal.ZERO) > 0 || returnAmount.compareTo(BigDecimal.ZERO) > 0) {
            purchase.setPaymentStatus(PaymentStatus.Partial);
        } else {
            purchase.setPaymentStatus(PaymentStatus.Pending);
        }
        purchaseRepository.save(purchase);
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal originalPurchaseNet(Purchase purchase) {
        // Align with purchase payable net: gross - discount + otherCharges ± tax - WHT
        BigDecimal gross = safe(purchase.getTotalAmount()).subtract(safe(purchase.getDiscountAmount()))
                .add(safe(purchase.getOtherCharges()));
        String taxMode = purchase.getTaxMode() == null ? "EXCLUSIVE" : purchase.getTaxMode();
        if (!"INCLUSIVE".equalsIgnoreCase(taxMode)) {
            gross = gross.add(safe(purchase.getTaxAmount()));
        }
        BigDecimal net = gross.subtract(safe(purchase.getWithholdingTaxAmount()));
        if (net.compareTo(BigDecimal.ZERO) > 0) return net;
        return safe(purchase.getNetAmount()).add(safe(purchase.getReturnAmount()));
    }

    private int purchasedQty(Purchase purchase, Integer productId) {
        if (purchase.getDetails() == null) return 0;
        return purchase.getDetails().stream()
                .filter(d -> d.getProduct() != null && productId.equals(d.getProduct().getId()))
                .mapToInt(d -> d.getQty() != null ? d.getQty() : 0)
                .sum();
    }

    private BigDecimal discountedUnitCost(Purchase purchase, Integer productId) {
        if (purchase.getDetails() == null) return BigDecimal.ZERO;
        int qty = purchasedQty(purchase, productId);
        if (qty <= 0) return BigDecimal.ZERO;

        BigDecimal productGross = purchase.getDetails().stream()
                .filter(d -> d.getProduct() != null && productId.equals(d.getProduct().getId()))
                .map(d -> safe(d.getSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal purchaseGross = safe(purchase.getTotalAmount());
        BigDecimal purchaseNet = originalPurchaseNet(purchase);
        if (purchaseGross.compareTo(BigDecimal.ZERO) <= 0) {
            return productGross.divide(BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal productNet = productGross.multiply(purchaseNet).divide(purchaseGross, 2, java.math.RoundingMode.HALF_UP);
        return productNet.divide(BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP);
    }

    private int returnedQty(Integer purchaseId, Integer productId, Integer excludeReturnId) {
        return purchaseReturnRepository.findByPurchaseId(purchaseId).stream()
                .filter(this::isConfirmed)
                .filter(r -> excludeReturnId == null || !excludeReturnId.equals(r.getId()))
                .flatMap(r -> r.getDetails().stream())
                .filter(d -> d.getProduct() != null && productId.equals(d.getProduct().getId()))
                .mapToInt(d -> d.getQty() != null ? d.getQty() : 0)
                .sum();
    }

    private Set<String> purchasedSerials(Purchase purchase, Integer productId) {
        Set<String> serials = new HashSet<>();
        if (purchase.getDetails() == null) return serials;
        purchase.getDetails().stream()
                .filter(d -> d.getProduct() != null && productId.equals(d.getProduct().getId()))
                .filter(d -> d.getWarrantyItems() != null)
                .flatMap(d -> d.getWarrantyItems().stream())
                .map(w -> w.getSerialNumber())
                .filter(sn -> sn != null && !sn.isBlank())
                .map(sn -> sn.trim().toUpperCase())
                .forEach(serials::add);
        return serials;
    }

    private boolean isSerialAlreadyReturned(Integer purchaseId, Integer productId, String serial, Integer excludeReturnId) {
        String normalized = serial == null ? "" : serial.trim().toUpperCase();
        return purchaseReturnRepository.findByPurchaseId(purchaseId).stream()
                .filter(this::isConfirmed)
                .filter(r -> excludeReturnId == null || !excludeReturnId.equals(r.getId()))
                .flatMap(r -> r.getDetails().stream())
                .filter(d -> d.getProduct() != null && productId.equals(d.getProduct().getId()))
                .flatMap(d -> normalizeSerials(d.getSerialNumber() == null ? List.of() : List.of(d.getSerialNumber())).stream())
                .anyMatch(sn -> sn.equalsIgnoreCase(normalized));
    }

    private boolean isConfirmed(PurchaseReturn purchaseReturn) {
        return purchaseReturn != null && !STATUS_VOIDED.equalsIgnoreCase(purchaseReturn.getStatus());
    }

    private boolean isPosted(PurchaseReturn purchaseReturn) {
        return purchaseReturn != null && STATUS_SETTLED.equalsIgnoreCase(purchaseReturn.getStatus());
    }

    private PurchaseDetailWarranty findPurchaseWarranty(Purchase purchase, Integer productId, String serial) {
        if (purchase == null || purchase.getDetails() == null || serial == null) return null;
        String normalized = serial.trim().toUpperCase();
        return purchase.getDetails().stream()
                .filter(d -> d.getProduct() != null && productId.equals(d.getProduct().getId()))
                .filter(d -> d.getWarrantyItems() != null)
                .flatMap(d -> d.getWarrantyItems().stream())
                .filter(w -> w.getSerialNumber() != null && normalized.equals(w.getSerialNumber().trim().toUpperCase()))
                .findFirst()
                .orElse(null);
    }

    private List<String> normalizeSerials(List<String> serials) {
        if (serials == null) return List.of();
        return serials.stream()
                .flatMap(sn -> sn == null ? java.util.stream.Stream.empty() : java.util.Arrays.stream(sn.split(",")))
                .map(String::trim)
                .filter(sn -> !sn.isBlank())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    private String generateReturnNo() {
        Integer lastId = purchaseReturnRepository.findTopByOrderByIdDesc().map(PurchaseReturn::getId).orElse(0);
        var cfg = companySettingsService.getSettings();
        String prefix = cfg.getPurchaseReturnPrefix() != null && !cfg.getPurchaseReturnPrefix().isBlank()
                ? cfg.getPurchaseReturnPrefix().trim() : "PRN";
        int digits = cfg.getPurchaseReturnDigits() != null ? cfg.getPurchaseReturnDigits() : 5;
        return String.format("%s-%0" + digits + "d", prefix, lastId + 1);
    }

    private String generateTransactionNo() {
        Long count = paymentTransactionRepository.count();
        return String.format("TXN-%06d", count + 1);
    }

    private void recordPaymentTransactions(PurchaseReturn pr, BigDecimal refundAmount, String fallbackTransactionNo,
                                           PaymentMethod fallbackMethod, List<PaymentTransactionDTO> payments) {
        for (PaymentLine line : resolvePaymentLines(payments, refundAmount, fallbackMethod)) {
            PaymentTransaction paymentTx = new PaymentTransaction();
            paymentTx.setReferenceId(pr.getId());
            paymentTx.setReferenceType(ReferenceType.Purchase_Return);
            paymentTx.setPaymentMethod(line.method());
            paymentTx.setAmount(line.amount());
            paymentTx.setPaymentDate(LocalDateTime.now());
            paymentTx.setTransactionNo(line.transactionNo() != null && !line.transactionNo().isBlank()
                    ? line.transactionNo()
                    : (fallbackTransactionNo == null || fallbackTransactionNo.isBlank() ? generateTransactionNo() : fallbackTransactionNo));
            paymentTransactionRepository.save(paymentTx);
            if (isCashMethod(line.method())) {
                cashDrawerService.recordPurchaseCashIn(line.amount(),
                        "Purchase return refund " + pr.getReturnNo());
            }
        }
    }

    private BigDecimal paymentTotal(List<PaymentTransactionDTO> payments, BigDecimal fallback) {
        if (payments == null || payments.isEmpty()) return fallback != null ? fallback : BigDecimal.ZERO;
        return payments.stream()
                .map(PaymentTransactionDTO::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PaymentLine> resolvePaymentLines(List<PaymentTransactionDTO> payments, BigDecimal expectedTotal, PaymentMethod fallbackMethod) {
        if (payments == null || payments.isEmpty()) {
            if (fallbackMethod == null) throw new RuntimeException("Payment Method is required.");
            if (fallbackMethod.getAccount() == null) throw new RuntimeException("Payment Method must have linked account.");
            return List.of(new PaymentLine(fallbackMethod, expectedTotal, null));
        }
        BigDecimal total = paymentTotal(payments, BigDecimal.ZERO);
        if (expectedTotal != null && total.compareTo(expectedTotal) != 0) {
            throw new RuntimeException("Split payment total must equal refund amount.");
        }
        List<PaymentLine> lines = new ArrayList<>();
        for (PaymentTransactionDTO payment : payments) {
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            PaymentMethod method = paymentMethodRepository.findById(payment.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            if (method.getAccount() == null) throw new RuntimeException("Payment Method must have linked account.");
            lines.add(new PaymentLine(method, amount, payment.getTransactionNo()));
        }
        return lines;
    }

    private record PaymentLine(PaymentMethod method, BigDecimal amount, String transactionNo) {}

    private String joinSerials(List<String> serials) {
        return serials == null ? null : String.join(",", serials);
    }

    private void createReturnJournal(PurchaseReturn pr, PaymentMethod method, BigDecimal refundAmount,
                                     BigDecimal payableReduction, BigDecimal supplierCreditIncrease,
                                     Integer staffId, String supplierName,
                                     List<PaymentTransactionDTO> payments, BigDecimal supplierShippingPortion) {
        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(pr.getReturnNo());
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Purchase Return from Supplier: " + supplierName);
        journalDTO.setStaffId(staffId);

        List<JournalDetailDTO> details = new ArrayList<>();

        BigDecimal totalCredit = BigDecimal.ZERO;

        if (payableReduction != null && payableReduction.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO drPayable = new JournalDetailDTO();
            drPayable.setAccountId(accountResolver.payable().getId());
            drPayable.setDebit(payableReduction);
            drPayable.setCredit(BigDecimal.ZERO);
            details.add(drPayable);
            totalCredit = totalCredit.add(payableReduction);
        }

        if (refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            for (PaymentLine line : resolvePaymentLines(payments, refundAmount, method)) {
                JournalDetailDTO drCashBank = new JournalDetailDTO();
                drCashBank.setAccountId(line.method().getAccount().getId());
                drCashBank.setDebit(line.amount());
                drCashBank.setCredit(BigDecimal.ZERO);
                details.add(drCashBank);
            }
            totalCredit = totalCredit.add(refundAmount);
        }

        if (supplierCreditIncrease != null && supplierCreditIncrease.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO drSupplierCredit = new JournalDetailDTO();
            drSupplierCredit.setAccountId(accountResolver.supplierAdvance().getId());
            drSupplierCredit.setDebit(supplierCreditIncrease);
            drSupplierCredit.setCredit(BigDecimal.ZERO);
            details.add(drSupplierCredit);
            totalCredit = totalCredit.add(supplierCreditIncrease);
        }

        BigDecimal supplierShipping = safe(supplierShippingPortion);
        if (supplierShipping.signum() > 0) {
            JournalDetailDTO drShipping = new JournalDetailDTO();
            drShipping.setAccountId(accountResolver.transportation().getId());
            drShipping.setDebit(supplierShipping);
            drShipping.setCredit(BigDecimal.ZERO);
            details.add(drShipping);
        }

        if (totalCredit.compareTo(settlementValue(pr)) != 0) {
            throw new IllegalStateException("Purchase return journal settlement components do not equal expected supplier credit.");
        }

        // Credit Purchase Return (COA code INC-007)
        JournalDetailDTO crPurchaseReturn = new JournalDetailDTO();
        crPurchaseReturn.setAccountId(accountResolver.purchaseRtn().getId());
        crPurchaseReturn.setDebit(BigDecimal.ZERO);
        crPurchaseReturn.setCredit(totalCredit.add(supplierShipping));
        details.add(crPurchaseReturn);

        if (totalCredit.compareTo(BigDecimal.ZERO) > 0) {
            journalDTO.setDetails(details);
            journalWriter.write(journalDTO);
        }
    }

    private void createVoidJournal(PurchaseReturn pr, Integer staffId, String supplierName,
                                   BigDecimal payableReversal, BigDecimal supplierCreditReversal,
                                   List<PaymentTransaction> refundTransactions) {
        BigDecimal total = safe(pr.getTotalReturnAmount());
        if (total.compareTo(BigDecimal.ZERO) <= 0) return;

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(pr.getReturnNo() + "-VOID");
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Void Purchase Return from Supplier: " + supplierName);
        journalDTO.setStaffId(staffId);

        List<JournalDetailDTO> details = new ArrayList<>();

        JournalDetailDTO drPurchaseReturn = new JournalDetailDTO();
        drPurchaseReturn.setAccountId(accountResolver.purchaseRtn().getId());
        drPurchaseReturn.setDebit(total);
        drPurchaseReturn.setCredit(BigDecimal.ZERO);
        details.add(drPurchaseReturn);

        if (payableReversal.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO crPayable = new JournalDetailDTO();
            crPayable.setAccountId(accountResolver.payable().getId());
            crPayable.setDebit(BigDecimal.ZERO);
            crPayable.setCredit(payableReversal);
            details.add(crPayable);
        }

        for (PaymentTransaction tx : refundTransactions) {
            if (tx.getPaymentMethod() != null && tx.getPaymentMethod().getAccount() != null
                    && safe(tx.getAmount()).signum() > 0) {
                JournalDetailDTO crCash = new JournalDetailDTO();
                crCash.setAccountId(tx.getPaymentMethod().getAccount().getId());
                crCash.setDebit(BigDecimal.ZERO);
                crCash.setCredit(tx.getAmount());
                details.add(crCash);
            }
        }

        if (supplierCreditReversal.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO crSupplierCredit = new JournalDetailDTO();
            crSupplierCredit.setAccountId(accountResolver.supplierAdvance().getId());
            crSupplierCredit.setDebit(BigDecimal.ZERO);
            crSupplierCredit.setCredit(supplierCreditReversal);
            details.add(crSupplierCredit);
        }

        BigDecimal supplierShipping = safe(pr.getSupplierShippingPortion());
        if (supplierShipping.signum() > 0) {
            JournalDetailDTO crShipping = new JournalDetailDTO();
            crShipping.setAccountId(accountResolver.transportation().getId());
            crShipping.setDebit(BigDecimal.ZERO);
            crShipping.setCredit(supplierShipping);
            details.add(crShipping);
        }

        journalDTO.setDetails(details);
        journalWriter.write(journalDTO);
    }

    private void reverseRefundTransactions(PurchaseReturn pr, List<PaymentTransaction> transactions, String reason) {
        LocalDateTime now = LocalDateTime.now();
        for (PaymentTransaction tx : transactions) {
            tx.setReversed(true);
            tx.setReversedAt(now);
            tx.setReversedBy(currentActor());
            tx.setReversalReason(reason);
            paymentTransactionRepository.save(tx);
            if (isCashMethod(tx.getPaymentMethod())) {
                cashDrawerService.recordPurchaseCashOut(safe(tx.getAmount()),
                        "Void purchase return refund " + pr.getReturnNo());
            }
        }
    }

    private boolean isCashMethod(PaymentMethod method) {
        return method != null && method.getAccount() != null
                && method.getAccount().getId().equals(accountResolver.cash().getId());
    }

    private PurchaseReturn getReturn(Integer id) {
        return purchaseReturnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase return not found with id: " + id));
    }

    private void requireStatus(PurchaseReturn entity, String expected) {
        if (!expected.equalsIgnoreCase(entity.getStatus()))
            throw new IllegalStateException("Purchase return must be " + expected + " (current: " + entity.getStatus() + ")");
    }

    private PurchaseReturnDTO workflowSaved(PurchaseReturn entity, String event) {
        PurchaseReturn saved = purchaseReturnRepository.save(entity);
        recordActivity(saved, event, null, saved.getStatus(), null);
        messagingTemplate.convertAndSend(PURCHASE_RETURN_TOPIC, event);
        return toDto(saved);
    }

    @Transactional
    public PurchaseReturnDTO addAttachment(Integer id, PurchaseReturnAttachmentDTO dto) {
        PurchaseReturn entity=getReturn(id);
        if(dto==null || dto.getFileName()==null || dto.getFileName().isBlank() || dto.getDataUrl()==null || dto.getDataUrl().isBlank()) throw new IllegalArgumentException("Attachment file is required");
        if(dto.getDataUrl().length()>7_000_000) throw new IllegalArgumentException("Attachment exceeds 5 MB");
        attachmentRepository.save(PurchaseReturnAttachment.builder().purchaseReturn(entity).attachmentType(dto.getAttachmentType()==null?"OTHER":dto.getAttachmentType()).fileName(dto.getFileName()).contentType(dto.getContentType()).dataUrl(dto.getDataUrl()).uploadedBy(currentActor()).uploadedAt(LocalDateTime.now()).build());
        recordActivity(entity,"ATTACHMENT_ADDED",entity.getStatus(),entity.getStatus(),dto.getFileName()); return toDto(entity);
    }

    @Transactional
    public PurchaseReturnDTO deleteAttachment(Integer id,Integer attachmentId) { PurchaseReturn entity=getReturn(id); PurchaseReturnAttachment a=attachmentRepository.findById(attachmentId).orElseThrow(()->new ResourceNotFoundException("Attachment not found")); if(!a.getPurchaseReturn().getId().equals(id)) throw new IllegalArgumentException("Attachment does not belong to return"); attachmentRepository.delete(a); recordActivity(entity,"ATTACHMENT_DELETED",entity.getStatus(),entity.getStatus(),a.getFileName()); return toDto(entity); }

    private void recordActivity(PurchaseReturn entity,String event,String from,String to,String note){ activityRepository.save(PurchaseReturnActivity.builder().purchaseReturn(entity).eventType(event).fromStatus(from).toStatus(to).note(note).actor(currentActor()).occurredAt(LocalDateTime.now()).build()); }

    private PurchaseReturnReason resolveReason(Integer reasonId) {
        if (reasonId != null) {
            PurchaseReturnReason reason = returnReasonRepository.findById(reasonId)
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase return reason not found"));
            if (!Boolean.TRUE.equals(reason.getActive())) throw new IllegalArgumentException("Purchase return reason is inactive");
            return reason;
        }
        // Compatibility for existing Android/offline clients: map their required
        // header explanation to the seeded OTHER reason instead of inventing a master.
        return returnReasonRepository.findByCodeIgnoreCase("OTHER")
                .orElseThrow(() -> new IllegalStateException("Default OTHER purchase return reason is not configured"));
    }

    void configureShipping(PurchaseReturn entity, PurchaseReturnDTO dto) {
        applyNotNullDefaults(entity);
        BigDecimal totalCost = money(dto.getShippingCostAmount() != null
                ? dto.getShippingCostAmount() : entity.getShippingCostAmount());
        if (totalCost.signum() < 0) throw new IllegalArgumentException("Shipping cost cannot be negative");

        String payer = normalizeChoice(firstChoice(dto.getShippingPayerResponsibility(),
                entity.getShippingPayerResponsibility(), "COMPANY"),
                Set.of("COMPANY", "SUPPLIER", "SHARED"), "Shipping payer responsibility");
        String method = normalizeChoice(firstChoice(dto.getShippingAllocationMethod(),
                entity.getShippingAllocationMethod(), "VALUE"),
                Set.of("VALUE", "QUANTITY", "MANUAL"), "Shipping allocation method");

        BigDecimal company = dto.getCompanyShippingPortion() == null ? null : money(dto.getCompanyShippingPortion());
        BigDecimal supplier = dto.getSupplierShippingPortion() == null ? null : money(dto.getSupplierShippingPortion());
        if (company == null && supplier == null) {
            company = "SUPPLIER".equals(payer) ? BigDecimal.ZERO : totalCost;
            supplier = "SUPPLIER".equals(payer) ? totalCost : BigDecimal.ZERO;
        } else {
            company = company == null ? BigDecimal.ZERO : company;
            supplier = supplier == null ? BigDecimal.ZERO : supplier;
        }
        company = money(company);
        supplier = money(supplier);
        if (company.signum() < 0 || supplier.signum() < 0)
            throw new IllegalArgumentException("Shipping portions cannot be negative");
        if (company.add(supplier).compareTo(totalCost) != 0)
            throw new IllegalArgumentException("Company and supplier shipping portions must sum to shipping cost");
        if ("COMPANY".equals(payer) && (company.compareTo(totalCost) != 0 || supplier.signum() != 0))
            throw new IllegalArgumentException("COMPANY responsibility requires the company portion to equal total shipping cost");
        if ("SUPPLIER".equals(payer) && (supplier.compareTo(totalCost) != 0 || company.signum() != 0))
            throw new IllegalArgumentException("SUPPLIER responsibility requires the supplier portion to equal total shipping cost");
        if (supplier.compareTo(safe(entity.getTotalReturnAmount())) > 0)
            throw new IllegalArgumentException("Supplier shipping portion cannot exceed return value");

        entity.setShippingCostAmount(totalCost);
        entity.setShippingPayerResponsibility(payer);
        entity.setCompanyShippingPortion(company);
        entity.setSupplierShippingPortion(supplier);
        entity.setShippingAllocationMethod(method);
        entity.setShippingPaymentMethodId(dto.getShippingPaymentMethodId());
        entity.setShippingTransactionReference(trimToNull(dto.getShippingTransactionReference()));
        allocateShipping(entity, method, totalCost);
    }

    void allocateShipping(PurchaseReturn entity, String method, BigDecimal totalCost) {
        List<PurchaseReturnDetail> details = entity.getDetails() == null ? List.of() : entity.getDetails();
        if (details.isEmpty()) {
            if (totalCost.signum() > 0) throw new IllegalArgumentException("Shipping allocation requires return details");
            return;
        }
        if ("MANUAL".equals(method)) {
            BigDecimal allocated = details.stream().map(PurchaseReturnDetail::getAllocatedShippingCost)
                    .map(this::money).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (details.stream().anyMatch(d -> safe(d.getAllocatedShippingCost()).signum() < 0))
                throw new IllegalArgumentException("Allocated shipping cost cannot be negative");
            if (allocated.compareTo(totalCost) != 0)
                throw new IllegalArgumentException("Manual detail allocations must sum to shipping cost");
            details.forEach(d -> d.setAllocatedShippingCost(money(d.getAllocatedShippingCost())));
            return;
        }

        BigDecimal denominator = details.stream()
                .map(d -> "QUANTITY".equals(method) ? BigDecimal.valueOf(d.getQty() == null ? 0 : d.getQty()) : safe(d.getSubtotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalCost.signum() > 0 && denominator.signum() <= 0)
            throw new IllegalArgumentException("Shipping allocation basis must be greater than zero");
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < details.size(); i++) {
            PurchaseReturnDetail detail = details.get(i);
            BigDecimal amount;
            if (i == details.size() - 1) {
                amount = totalCost.subtract(allocated);
            } else if (totalCost.signum() == 0) {
                amount = BigDecimal.ZERO.setScale(2);
            } else {
                BigDecimal basis = "QUANTITY".equals(method)
                        ? BigDecimal.valueOf(detail.getQty() == null ? 0 : detail.getQty())
                        : safe(detail.getSubtotal());
                amount = totalCost.multiply(basis).divide(denominator, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(amount);
            }
            detail.setAllocatedShippingCost(money(amount));
        }
    }

    private void postCompanyShipping(PurchaseReturn entity) {
        BigDecimal amount = money(entity.getCompanyShippingPortion());
        if (amount.signum() == 0) {
            entity.setShippingPostedAt(LocalDateTime.now());
            return;
        }
        if (!paymentTransactionRepository.findByReferenceIdAndReferenceType(
                entity.getId(), ReferenceType.Purchase_Return_Shipping).isEmpty()) {
            entity.setShippingPostedAt(LocalDateTime.now());
            return;
        }
        if (entity.getShippingPaymentMethodId() == null)
            throw new IllegalArgumentException("Shipping payment method is required for company-paid shipping");
        PaymentMethod method = paymentMethodRepository.findById(entity.getShippingPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Shipping payment method not found"));
        if (method.getAccount() == null)
            throw new IllegalArgumentException("Shipping payment method must have a linked account");

        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceId(entity.getId());
        tx.setReferenceType(ReferenceType.Purchase_Return_Shipping);
        tx.setPaymentMethod(method);
        tx.setAmount(amount);
        tx.setPaymentDate(entity.getDispatchedAt());
        tx.setTransactionNo(entity.getShippingTransactionReference() == null
                ? generateTransactionNo() : entity.getShippingTransactionReference());
        paymentTransactionRepository.save(tx);

        JournalDetailDTO drExpense = new JournalDetailDTO();
        drExpense.setAccountId(accountResolver.transportation().getId());
        drExpense.setDebit(amount);
        drExpense.setCredit(BigDecimal.ZERO);
        JournalDetailDTO crPayment = new JournalDetailDTO();
        crPayment.setAccountId(method.getAccount().getId());
        crPayment.setDebit(BigDecimal.ZERO);
        crPayment.setCredit(amount);
        JournalEntryDTO journal = new JournalEntryDTO();
        journal.setReferenceNo(entity.getReturnNo() + "-SHIP");
        journal.setEntryDate(entity.getDispatchedAt());
        journal.setDescription("Return shipping dispatch " + entity.getReturnNo());
        journal.setStaffId(entity.getPurchase() != null && entity.getPurchase().getStaff() != null
                ? entity.getPurchase().getStaff().getId() : null);
        journal.setDetails(List.of(drExpense, crPayment));
        journalWriter.write(journal);
        if (isCashMethod(method))
            cashDrawerService.recordPurchaseCashOut(amount, "Return shipping " + entity.getReturnNo());
        entity.setShippingPostedAt(LocalDateTime.now());
    }

    BigDecimal settlementValue(PurchaseReturn entity) {
        return money(safe(entity.getTotalReturnAmount()).subtract(safe(entity.getSupplierShippingPortion())).max(BigDecimal.ZERO));
    }

    private BigDecimal money(BigDecimal value) {
        return safe(value).setScale(2, RoundingMode.HALF_UP);
    }

    void applyNotNullDefaults(PurchaseReturn entity) {
        if (entity.getShippingCostAmount() == null) {
            entity.setShippingCostAmount(BigDecimal.ZERO);
        }
        if (entity.getShippingPayerResponsibility() == null || entity.getShippingPayerResponsibility().isBlank()) {
            entity.setShippingPayerResponsibility("COMPANY");
        }
        if (entity.getCompanyShippingPortion() == null) {
            entity.setCompanyShippingPortion(BigDecimal.ZERO);
        }
        if (entity.getSupplierShippingPortion() == null) {
            entity.setSupplierShippingPortion(BigDecimal.ZERO);
        }
        if (entity.getShippingAllocationMethod() == null || entity.getShippingAllocationMethod().isBlank()) {
            entity.setShippingAllocationMethod("VALUE");
        }
    }

    private String firstChoice(String dtoValue, String entityValue, String fallback) {
        if (dtoValue != null && !dtoValue.isBlank()) return dtoValue;
        if (entityValue != null && !entityValue.isBlank()) return entityValue;
        return fallback;
    }

    private String normalizeChoice(String value, Set<String> allowed, String label) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!allowed.contains(normalized))
            throw new IllegalArgumentException(label + " must be one of " + String.join(", ", allowed));
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSettlementType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        if (!Set.of("REFUND", "CREDIT_NOTE", "REPLACEMENT", "OFFSET", "SPLIT").contains(type))
            throw new IllegalArgumentException("Settlement type must be REFUND, CREDIT_NOTE, REPLACEMENT, OFFSET or SPLIT");
        return type;
    }

    private String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "system";
    }
}
