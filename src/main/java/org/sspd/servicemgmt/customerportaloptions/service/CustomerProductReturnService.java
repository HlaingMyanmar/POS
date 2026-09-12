package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.customerportaloptions.dto.*;
import org.sspd.servicemgmt.customerportaloptions.model.*;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerOrderRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerProductReturnPhotoRepository;
import org.sspd.servicemgmt.customerportaloptions.repository.CustomerProductReturnRepository;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.dataevent.DataEventPublisher;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerProductReturnService {

    static final Set<String> OPEN = Set.of("REQUESTED", "APPROVED", "RETURNED", "INSPECTING");
    static final Set<String> QTY_HELD = Set.of("REQUESTED", "APPROVED", "RETURNED", "INSPECTING", "REFUNDED", "REPLACED", "CLOSED");
    static final Set<String> DISPOSITIONS = Set.of("SELLABLE", "DAMAGED", "QUARANTINE");
    static final Set<String> OUTCOMES = Set.of("REFUNDED", "REPLACED", "CLOSED");
    static final List<String> DELIVERY_FLOW = List.of(
            "PICKUP_REQUESTED", "PICKED_UP", "RETURN_IN_TRANSIT", "RECEIVED_BY_SHOP");

    private final CustomerProductReturnRepository returns;
    private final CustomerProductReturnPhotoRepository photos;
    private final CustomerOrderRepository orders;
    private final SaleRepository sales;
    private final PaymentMethodRepository methods;
    private final PaymentTransactionRepository paymentTransactions;
    private final DataEventPublisher events;
    private final EntityManager entityManager;
    private final ProductSerialRepository serials;
    private final StockMovementService stockMovements;
    private final AccountResolver accountResolver;
    private final JournalWriter journalWriter;

    @Transactional
    public CustomerProductReturnDTO submit(Integer orderId, CustomerProductReturnRequest request, List<MultipartFile> images) throws java.io.IOException {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        CustomerOrder order = orderId == null ? null : orders.findByIdWithLines(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (order != null && !order.getCustomer().getId().equals(customerId)) {
            throw new AccessDeniedException("Not your order");
        }
        Integer saleId = request.getSaleId() != null ? request.getSaleId() : (order == null ? null : order.getCompletedSaleId());
        if (saleId == null) throw new IllegalStateException("Sale voucher ရှိမှသာ ပစ္စည်းပြန်ပို့ တောင်းနိုင်သည်");
        Sale sale = sales.findLockedWithDetails(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found"));
        if (Boolean.TRUE.equals(sale.getVoided())) throw new IllegalStateException("ပယ်ဖျက်ပြီး ဘောင်ချာကို ပြန်ပို့မရပါ");
        if (!sale.getCustomer().getId().equals(customerId)) throw new AccessDeniedException("Not your sale");
        if (order != null && order.getCompletedSaleId() != null && !saleId.equals(order.getCompletedSaleId())) {
            throw new IllegalArgumentException("Order နှင့် Sale မကိုက်ပါ");
        }
        note(request.getReason(), "Return reason");
        List<CustomerProductReturnRequest.Line> wanted = request.getLines() == null ? List.of() : request.getLines();
        if (wanted.isEmpty()) throw new IllegalArgumentException("ပြန်ပို့မည့် ပစ္စည်း ရွေးပါ");

        Map<Integer, Integer> remaining = remainingQty(sale, order);
        List<CustomerProductReturnLine> built = new ArrayList<>();
        for (CustomerProductReturnRequest.Line line : wanted) {
            if (line.getProductId() == null || line.getQty() == null || line.getQty() < 1) {
                throw new IllegalArgumentException("ပစ္စည်းနှင့် အရေအတွက် မှန်ကန်ရမည်");
            }
            int left = remaining.getOrDefault(line.getProductId(), 0);
            if (line.getQty() > left) throw new IllegalArgumentException("ပြန်ပို့နိုင်သော အရေအတွက် မလုံလောက်ပါ");
            remaining.put(line.getProductId(), left - line.getQty());
            SaleDetail match = sale.getDetails().stream()
                    .filter(d -> d.getProduct() != null && line.getProductId().equals(d.getProduct().getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Sale တွင် ဤပစ္စည်း မရှိပါ"));
            String serial = line.getSerialNumber() == null ? null : line.getSerialNumber().trim();
            if (serial != null && serial.isBlank()) serial = null;
            if (serial != null && serial.length() > 120) throw new IllegalArgumentException("Serial max 120 characters");
            if (Boolean.TRUE.equals(match.getProduct().getHasSerial())
                    && (serial == null || line.getQty() != 1)) {
                throw new IllegalArgumentException("Serial products require quantity one and the sold serial number");
            }
            if (serial != null) {
                String want = serial;
                boolean onSale = sale.getDetails().stream()
                        .anyMatch(d -> d.getProduct() != null && line.getProductId().equals(d.getProduct().getId())
                                && want.equalsIgnoreCase(d.getSerialNumber()));
                if (!onSale) throw new IllegalArgumentException("Serial သည် ဤဘောင်ချာပေါ် မရှိပါ");
                boolean alreadyReturned = returns.findBySaleIdOrderByIdDesc(saleId).stream()
                        .filter(r -> QTY_HELD.contains(r.getStatus()) && r.getLines() != null)
                        .flatMap(r -> r.getLines().stream())
                        .anyMatch(l -> l.getSerialNumber() != null && want.equalsIgnoreCase(l.getSerialNumber()));
                if (alreadyReturned) throw new IllegalStateException("Serial has already been returned");
            }
            BigDecimal unit = match.getUnitPrice() == null ? BigDecimal.ZERO : match.getUnitPrice();
            CustomerProductReturnLine row = CustomerProductReturnLine.builder()
                    .saleDetailId(match.getId())
                    .productId(line.getProductId())
                    .productName(match.getProduct().getName())
                    .qty(line.getQty())
                    .unitPrice(unit)
                    .subtotal(unit.multiply(BigDecimal.valueOf(line.getQty())).setScale(2, RoundingMode.HALF_UP))
                    .serialNumber(serial)
                    .build();
            if (order != null && order.getLines() != null) {
                order.getLines().stream()
                        .filter(ol -> ol.getProduct() != null && line.getProductId().equals(ol.getProduct().getId()))
                        .findFirst()
                        .ifPresent(ol -> row.setOrderLineId(ol.getId()));
            }
            built.add(row);
        }

        CustomerProductReturn entity = CustomerProductReturn.builder()
                .returnNo("PENDING")
                .order(order)
                .saleId(sale.getId())
                .customer(sale.getCustomer())
                .status("REQUESTED")
                .reason(request.getReason().trim())
                .customerNote(blankToNull(request.getNote()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        for (CustomerProductReturnLine line : built) {
            line.setProductReturn(entity);
            entity.getLines().add(line);
        }
        entity = returns.saveAndFlush(entity);
        entity.setReturnNo(String.format("CR-%06d", entity.getId()));
        if (images != null) {
            for (MultipartFile image : images) {
                addPhoto(entity, image);
            }
        }
        entity = returns.saveAndFlush(entity);
        notify(entity, "ပစ္စည်းပြန်ပို့ တောင်းဆိုချက် ရပါပြီ။ ဆိုင်က စစ်ဆေးပါမည်။");
        return toDto(entity, false);
    }

    @Transactional
    public CustomerProductReturnDTO addPhotos(Integer id, List<MultipartFile> images) throws java.io.IOException {
        CustomerProductReturn entity = owned(id);
        if (!"REQUESTED".equals(entity.getStatus())) throw new IllegalStateException("တောင်းဆိုချိန်မှသာ ဓာတ်ပုံ ထပ်တင်နိုင်သည်");
        if (images == null || images.isEmpty()) throw new IllegalArgumentException("ဓာတ်ပုံ တင်ပါ");
        int existing = entity.getPhotos() == null ? 0 : entity.getPhotos().size();
        if (existing + images.size() > 8) throw new IllegalArgumentException("ဓာတ်ပုံ အများဆုံး ၈ ပုံ");
        for (MultipartFile image : images) addPhoto(entity, image);
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(returns.saveAndFlush(entity), false);
    }

    @Transactional(readOnly = true)
    public List<CustomerProductReturnDTO> mine() {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        return returns.findByCustomer_IdOrderByIdDesc(customerId).stream().map(r -> toDto(r, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerProductReturnDTO> forOrder(Integer orderId) {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        CustomerOrder order = orders.findByIdWithLines(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getCustomer().getId().equals(customerId)) throw new AccessDeniedException("Not your order");
        return returns.findByOrder_IdOrderByIdDesc(orderId).stream().map(r -> toDto(r, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerProductReturnDTO> shopList() {
        return returns.findAllByOrderByIdDesc().stream().map(r -> toDto(r, false)).toList();
    }

    @Transactional(readOnly = true)
    public CustomerProductReturnDTO shopGet(Integer id, boolean includeImages) {
        return toDto(returns.findByIdWithLines(id).orElseThrow(() -> new ResourceNotFoundException("Return not found")), includeImages);
    }

    @Transactional
    public CustomerProductReturnDTO review(Integer id, CustomerProductReturnReviewRequest request) {
        CustomerProductReturn entity = returns.findLockedByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found"));
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(action)) {
            if (!"REQUESTED".equals(entity.getStatus())) throw new IllegalStateException("REQUESTED အခြေအနေမှသာ လက်ခံနိုင်သည်");
            note(request.getNote(), "Review note");
            entity.setStatus("APPROVED");
            entity.setReviewNote(request.getNote().trim());
            entity.setReviewedBy(actor());
            entity.setReviewedAt(LocalDateTime.now());
            notify(entity, "ပြန်ပို့ တောင်းဆိုချက် လက်ခံပါသည်။ ပစ္စည်း ပြန်ပို့ပါ။");
        } else if ("REJECT".equals(action)) {
            if (!"REQUESTED".equals(entity.getStatus())) throw new IllegalStateException("REQUESTED အခြေအနေမှသာ ငြင်းပယ်နိုင်သည်");
            note(request.getNote(), "Review note");
            entity.setStatus("REJECTED");
            entity.setReviewNote(request.getNote().trim());
            entity.setReviewedBy(actor());
            entity.setReviewedAt(LocalDateTime.now());
            entity.setCompletedAt(LocalDateTime.now());
            notify(entity, "ပြန်ပို့ တောင်းဆိုချက် ငြင်းပယ်ပါသည်။ — " + request.getNote().trim());
        } else if ("DELIVERY".equals(action)) {
            updateDelivery(entity, request);
        } else if ("RECEIVED".equals(action)) {
            if (!"APPROVED".equals(entity.getStatus())) throw new IllegalStateException("APPROVED ပြီးမှ ပစ္စည်းလက်ခံ မှတ်နိုင်သည်");
            if (!"RECEIVED_BY_SHOP".equals(entity.getDeliveryStatus())) {
                throw new IllegalStateException("Return delivery must reach RECEIVED_BY_SHOP first");
            }
            entity.setStatus("RETURNED");
            entity.setReceivedBy(actor());
            entity.setReceivedAt(LocalDateTime.now());
            notify(entity, "ဆိုင်က ပစ္စည်း ပြန်လက်ခံပြီးပါပြီ။ စစ်ဆေးပါမည်။");
        } else if ("INSPECT".equals(action)) {
            if (!Set.of("RETURNED", "INSPECTING").contains(entity.getStatus())) {
                throw new IllegalStateException("ပစ္စည်းပြန်ရပြီးမှ စစ်ဆေးနိုင်သည်");
            }
            if (entity.getReceivedAt() == null) {
                throw new IllegalStateException("Shop receipt is required before inspection");
            }
            entity.setStatus("INSPECTING");
            entity.setInspectedBy(actor());
            entity.setInspectedAt(LocalDateTime.now());
            if (request.getNote() != null && !request.getNote().isBlank()) entity.setInspectNote(request.getNote().trim());
            notify(entity, "ဆိုင်က ပြန်ပို့ပစ္စည်း စစ်ဆေးနေသည်။");
        } else if ("COMPLETE".equals(action)) {
            complete(entity, request);
        } else {
            throw new IllegalArgumentException("Unknown return action");
        }
        entity.setUpdatedAt(LocalDateTime.now());
        return toDto(returns.saveAndFlush(entity), false);
    }

    private void complete(CustomerProductReturn entity, CustomerProductReturnReviewRequest request) {
        if (!"INSPECTING".equals(entity.getStatus()) || entity.getReceivedAt() == null) {
            throw new IllegalStateException("ပစ္စည်းပြန်ရပြီး စစ်ဆေးပြီးမှ အပြီးသတ်နိုင်သည်");
        }
        if (entity.isInventoryApplied() || entity.isAccountingPosted()) {
            throw new IllegalStateException("Return completion has already been applied");
        }
        String outcome = request.getOutcome() == null ? "" : request.getOutcome().trim().toUpperCase(Locale.ROOT);
        if (!OUTCOMES.contains(outcome)) throw new IllegalArgumentException("REFUNDED / REPLACED / CLOSED ရွေးပါ");
        applyDispositions(entity, request.getLines());
        applyInventory(entity, request, outcome);
        if (request.getNote() != null && !request.getNote().isBlank()) entity.setInspectNote(request.getNote().trim());
        entity.setInspectedBy(actor());
        entity.setInspectedAt(LocalDateTime.now());
        entity.setCompletedAt(LocalDateTime.now());
        if ("REFUNDED".equals(outcome)) {
            BigDecimal max = entity.getLines().stream()
                    .map(l -> l.getSubtotal() == null ? BigDecimal.ZERO : l.getSubtotal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = request.getRefundAmount();
            if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() > 15 || amount.compareTo(max) > 0) {
                throw new IllegalArgumentException("ပြန်အမ်းငွေ 0 နှင့် " + max + " Ks ကြား ဖြစ်ရမည်");
            }
            if (request.getPaymentMethodId() == null) throw new IllegalArgumentException("ပြန်အမ်း channel ရွေးပါ");
            var method = methods.findById(request.getPaymentMethodId())
                    .orElseThrow(() -> new IllegalArgumentException("Payment channel not found"));
            if (!method.isActive()) throw new IllegalArgumentException("Payment channel is not active");
            String ref = request.getTransactionNo() == null ? null : request.getTransactionNo().trim().toUpperCase(Locale.ROOT);
            if (ref == null || ref.isBlank()) throw new IllegalArgumentException("ပြန်အမ်း transaction reference ထည့်ပါ");
            if (ref.length() > 120) throw new IllegalArgumentException("Transaction reference max 120 characters");
            PaymentTransaction tx = new PaymentTransaction();
            tx.setReferenceId(entity.getId());
            tx.setReferenceType(ReferenceType.Customer_Order_Return);
            tx.setPaymentMethod(method);
            tx.setAmount(amount.negate());
            tx.setPaymentDate(LocalDateTime.now());
            tx.setTransactionNo(ref);
            paymentTransactions.save(tx);
            postRefundJournal(entity, method.getAccount().getId(), amount);
            entity.setRefundAmount(amount.setScale(2, RoundingMode.HALF_UP));
            entity.setRefundPaymentMethodId(method.getId());
            entity.setRefundReference(ref);
            entity.setRefundedAt(LocalDateTime.now());
            entity.setRefundRecordedBy(actor());
            entity.setStatus("REFUNDED");
            entity.setAccountingPosted(true);
            notify(entity, "ပစ္စည်းပြန်ပို့ငွေ " + amount + " Ks ပြန်အမ်းမှတ်ပြီးပါပြီ။");
        } else if ("REPLACED".equals(outcome)) {
            entity.setStatus("REPLACED");
            entity.setAccountingPosted(true);
            notify(entity, "ပစ္စည်း အစားထိုးပေးမည်ဟု ဆုံးဖြတ်ပါသည်။");
        } else {
            entity.setStatus("CLOSED");
            entity.setAccountingPosted(true);
            notify(entity, "ပြန်ပို့ တောင်းဆိုချက် ပိတ်လိုက်ပါသည်။");
        }
    }

    private void updateDelivery(CustomerProductReturn entity, CustomerProductReturnReviewRequest request) {
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new IllegalStateException("Only an APPROVED return can move through delivery");
        }
        String next = request.getDeliveryStatus() == null ? "" :
                request.getDeliveryStatus().trim().toUpperCase(Locale.ROOT);
        int currentIndex = DELIVERY_FLOW.indexOf(entity.getDeliveryStatus());
        int nextIndex = DELIVERY_FLOW.indexOf(next);
        if (currentIndex < 0 || nextIndex != currentIndex + 1) {
            throw new IllegalStateException("Return delivery can only move forward by one step");
        }
        entity.setDeliveryStatus(next);
        entity.setDeliveryNote(blankToNull(request.getNote()));
        entity.setDeliveryUpdatedAt(LocalDateTime.now());
        entity.setDeliveryUpdatedBy(actor());
        notify(entity, "Return delivery updated: " + next);
    }

    private void applyInventory(CustomerProductReturn entity,
                                CustomerProductReturnReviewRequest request,
                                String outcome) {
        Map<Integer, CustomerProductReturnReviewRequest.Line> requested =
                request.getLines().stream().collect(Collectors.toMap(
                        CustomerProductReturnReviewRequest.Line::getId, l -> l));
        for (CustomerProductReturnLine line : entity.getLines()) {
            Product product = entityManager.find(
                    Product.class, line.getProductId(), LockModeType.PESSIMISTIC_WRITE);
            if (product == null) throw new ResourceNotFoundException("Return product not found");
            String disposition = line.getDisposition();
            if (Boolean.TRUE.equals(product.getHasSerial())) {
                if (line.getQty() != 1 || line.getSerialNumber() == null) {
                    throw new IllegalStateException("A serial return requires quantity one and a serial");
                }
                var serial = serials.findLockedBySerialNumber(line.getSerialNumber())
                        .orElseThrow(() -> new IllegalStateException("Returned serial not found"));
                if (!product.getId().equals(serial.getProduct().getId())
                        || serial.getStatus() != SerialStatus.Sold) {
                    throw new IllegalStateException("Returned serial is not in Sold state");
                }
                serial.setStatus("SELLABLE".equals(disposition) ? SerialStatus.Available
                        : "QUARANTINE".equals(disposition)
                        ? SerialStatus.Quarantined : SerialStatus.Damaged);
                serials.save(serial);
            } else if ("SELLABLE".equals(disposition)) {
                product.setStockQty(nvl(product.getStockQty()) + line.getQty());
            } else if ("QUARANTINE".equals(disposition)) {
                product.setStockQty(nvl(product.getStockQty()) + line.getQty());
                product.setQuarantinedQty(nvl(product.getQuarantinedQty()) + line.getQty());
            }
            stockMovements.recordMovement(StockMovement.builder()
                    .product(product).movementType(MovementType.RETURN).qty(line.getQty())
                    .referenceId(entity.getId()).referenceType("CustomerReturn").build());
            if ("REPLACED".equals(outcome)) {
                issueReplacement(entity, line, requested.get(line.getId()), product);
            }
        }
        entity.setInventoryApplied(true);
    }

    private void issueReplacement(CustomerProductReturn entity,
                                  CustomerProductReturnLine line,
                                  CustomerProductReturnReviewRequest.Line requested,
                                  Product product) {
        if (requested == null || requested.getReplacementProductId() == null
                || !product.getId().equals(requested.getReplacementProductId())) {
            throw new IllegalArgumentException("Replacement product must match the original product");
        }
        line.setReplacementProductId(requested.getReplacementProductId());
        if (Boolean.TRUE.equals(product.getHasSerial())) {
            String number = requested.getReplacementSerialNumber() == null ? ""
                    : requested.getReplacementSerialNumber().trim();
            if (number.isEmpty() || number.equalsIgnoreCase(line.getSerialNumber())) {
                throw new IllegalArgumentException("Choose a different available replacement serial");
            }
            var replacement = serials.findLockedBySerialNumber(number)
                    .orElseThrow(() -> new IllegalArgumentException("Replacement serial not found"));
            if (!product.getId().equals(replacement.getProduct().getId())
                    || replacement.getStatus() != SerialStatus.Available) {
                throw new IllegalStateException("Replacement serial is not available");
            }
            replacement.setStatus(SerialStatus.Sold);
            serials.save(replacement);
            line.setReplacementSerialNumber(number);
        } else {
            int available = nvl(product.getStockQty()) - nvl(product.getQuarantinedQty())
                    - nvl(product.getCustomerReservedQty());
            if (available < line.getQty()) {
                throw new IllegalStateException("Replacement stock is insufficient");
            }
            product.setStockQty(nvl(product.getStockQty()) - line.getQty());
        }
        stockMovements.recordMovement(StockMovement.builder()
                .product(product).movementType(MovementType.OUT).qty(line.getQty())
                .referenceId(entity.getId()).referenceType("CustomerReturnReplacement").build());
    }

    private void postRefundJournal(CustomerProductReturn entity,
                                   Integer paymentAccountId,
                                   BigDecimal amount) {
        if (paymentAccountId == null) {
            throw new IllegalStateException("Refund payment method must be linked to an account");
        }
        JournalDetailDTO debit = new JournalDetailDTO();
        debit.setAccountId(accountResolver.salesRtn().getId());
        debit.setDebit(amount);
        debit.setCredit(BigDecimal.ZERO);
        JournalDetailDTO credit = new JournalDetailDTO();
        credit.setAccountId(paymentAccountId);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(amount);
        JournalEntryDTO journal = new JournalEntryDTO();
        journal.setReferenceNo(entity.getReturnNo() + "-REFUND");
        journal.setEntryDate(LocalDateTime.now());
        journal.setDescription("Customer product return refund - " + entity.getReturnNo());
        journal.setDetails(List.of(debit, credit));
        journalWriter.write(journal);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private void applyDispositions(CustomerProductReturn entity, List<CustomerProductReturnReviewRequest.Line> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("ပစ္စည်းတစ်ခုချင်း stock disposition ရွေးပါ");
        Map<Integer, String> byId = new HashMap<>();
        for (CustomerProductReturnReviewRequest.Line line : lines) {
            if (line.getId() == null || line.getDisposition() == null) throw new IllegalArgumentException("disposition လိုအပ်သည်");
            String d = line.getDisposition().trim().toUpperCase(Locale.ROOT);
            if (!DISPOSITIONS.contains(d)) throw new IllegalArgumentException("SELLABLE / DAMAGED / QUARANTINE ရွေးပါ");
            byId.put(line.getId(), d);
        }
        for (CustomerProductReturnLine line : entity.getLines()) {
            String d = byId.get(line.getId());
            if (d == null) throw new IllegalArgumentException("Line disposition မပြည့်ပါ");
            line.setDisposition(d);
        }
    }

    private Map<Integer, Integer> remainingQty(Sale sale, CustomerOrder order) {
        Map<Integer, Integer> sold = new HashMap<>();
        if (sale.getDetails() != null) {
            for (SaleDetail d : sale.getDetails()) {
                if (d.getProduct() == null) continue;
                sold.merge(d.getProduct().getId(), d.getQty() == null ? 0 : d.getQty(), Integer::sum);
            }
        }
        List<CustomerProductReturn> existing = new ArrayList<>(returns.findBySaleIdOrderByIdDesc(sale.getId()));
        if (order != null) existing.addAll(returns.findByOrder_IdOrderByIdDesc(order.getId()));
        Set<Integer> seen = new HashSet<>();
        for (CustomerProductReturn r : existing) {
            if (!seen.add(r.getId())) continue;
            if (!QTY_HELD.contains(r.getStatus())) continue;
            if (r.getLines() == null) continue;
            for (CustomerProductReturnLine line : r.getLines()) {
                sold.merge(line.getProductId(), -line.getQty(), Integer::sum);
            }
        }
        sold.replaceAll((k, v) -> Math.max(v, 0));
        return sold;
    }

    private CustomerProductReturn owned(Integer id) {
        CustomerProductReturn entity = returns.findByIdWithLines(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found"));
        if (!entity.getCustomer().getId().equals(CustomerPortalAuth.require().getCustomerId())) {
            throw new AccessDeniedException("Not your return");
        }
        return entity;
    }

    private void addPhoto(CustomerProductReturn entity, MultipartFile image) throws java.io.IOException {
        if (image == null || image.isEmpty() || image.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("JPEG သို့မဟုတ် PNG 2 MB အောက် တင်ပါ");
        }
        byte[] data = image.getBytes();
        String mime;
        try (var stream = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
            var readers = javax.imageio.ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid return image");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png").contains(format)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) {
                    throw new IllegalArgumentException("Use a JPEG/PNG below 20 megapixels");
                }
                mime = format.equals("png") ? "image/png" : "image/jpeg";
            } finally {
                reader.dispose();
            }
        }
        CustomerProductReturnPhoto photo = CustomerProductReturnPhoto.builder()
                .productReturn(entity)
                .imageData(data)
                .imageType(mime)
                .submittedAt(LocalDateTime.now())
                .build();
        entity.getPhotos().add(photo);
    }

    private void notify(CustomerProductReturn entity, String text) {
        Integer orderId = entity.getOrder() == null ? null : entity.getOrder().getId();
        String orderNo = entity.getOrder() == null ? entity.getReturnNo() : entity.getOrder().getOrderNo();
        events.publishCustomerOrder("CUSTOMER_RETURN_UPDATED", orderId);
        CustomerPortalNotificationDTO dto = new CustomerPortalNotificationDTO();
        dto.setId(-entity.getId());
        dto.setOrderId(orderId);
        dto.setOrderNo(orderNo);
        dto.setStatus(entity.getStatus());
        dto.setChannel("CUSTOMER_RETURN");
        dto.setNote(entity.getReturnNo() + " — " + text);
        dto.setNotifiedAt(LocalDateTime.now());
        events.publishToUser(CustomerPortalAuth.usernameForCustomer(entity.getCustomer().getId()), "/topic/customer-orders", dto);
    }

    private CustomerProductReturnDTO toDto(CustomerProductReturn entity, boolean includeImages) {
        CustomerProductReturnDTO dto = new CustomerProductReturnDTO();
        dto.setId(entity.getId());
        dto.setReturnNo(entity.getReturnNo());
        if (entity.getOrder() != null) {
            dto.setOrderId(entity.getOrder().getId());
            dto.setOrderNo(entity.getOrder().getOrderNo());
        }
        dto.setSaleId(entity.getSaleId());
        dto.setCustomerId(entity.getCustomer().getId());
        dto.setCustomerName(entity.getCustomer().getName());
        dto.setStatus(entity.getStatus());
        dto.setDeliveryStatus(entity.getDeliveryStatus());
        dto.setDeliveryNote(entity.getDeliveryNote());
        dto.setDeliveryUpdatedAt(entity.getDeliveryUpdatedAt());
        dto.setInventoryApplied(entity.isInventoryApplied());
        dto.setAccountingPosted(entity.isAccountingPosted());
        dto.setReason(entity.getReason());
        dto.setCustomerNote(entity.getCustomerNote());
        dto.setReviewNote(entity.getReviewNote());
        dto.setInspectNote(entity.getInspectNote());
        dto.setRefundAmount(entity.getRefundAmount());
        dto.setRefundPaymentMethodId(entity.getRefundPaymentMethodId());
        if (entity.getRefundPaymentMethodId() != null) {
            methods.findById(entity.getRefundPaymentMethodId()).ifPresent(m -> dto.setRefundPaymentMethodName(m.getMethodName()));
        }
        dto.setRefundReference(entity.getRefundReference());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setReviewedAt(entity.getReviewedAt());
        dto.setReceivedAt(entity.getReceivedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        if (entity.getLines() != null) {
            dto.setLines(entity.getLines().stream().map(l -> {
                CustomerProductReturnDTO.Line line = new CustomerProductReturnDTO.Line();
                line.setId(l.getId());
                line.setProductId(l.getProductId());
                line.setProductName(l.getProductName());
                line.setQty(l.getQty());
                line.setUnitPrice(l.getUnitPrice());
                line.setSubtotal(l.getSubtotal());
                line.setSerialNumber(l.getSerialNumber());
                line.setDisposition(l.getDisposition());
                line.setReplacementProductId(l.getReplacementProductId());
                line.setReplacementSerialNumber(l.getReplacementSerialNumber());
                return line;
            }).collect(Collectors.toList()));
        }
        List<CustomerProductReturnPhoto> pics = entity.getPhotos();
        if (pics == null || pics.isEmpty()) pics = photos.findByProductReturn_IdOrderByIdAsc(entity.getId());
        dto.setPhotos(pics.stream().map(p -> {
            CustomerProductReturnDTO.Photo photo = new CustomerProductReturnDTO.Photo();
            photo.setId(p.getId());
            photo.setImageType(p.getImageType());
            photo.setSubmittedAt(p.getSubmittedAt());
            if (includeImages && p.getImageData() != null) {
                photo.setImage("data:" + p.getImageType() + ";base64," + Base64.getEncoder().encodeToString(p.getImageData()));
            }
            return photo;
        }).toList());
        return dto;
    }

    private static void note(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 1000) {
            throw new IllegalArgumentException(label + " is required (max 1000 characters)");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        if (t.isEmpty()) return null;
        if (t.length() > 1000) throw new IllegalArgumentException("Note max 1000 characters");
        return t;
    }

    private String actor() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }
}
