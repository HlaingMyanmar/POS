package org.sspd.servicemgmt.purchaseoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service.PaymentTransactionService;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.dto.PurchaseDTO;
import org.sspd.servicemgmt.purchaseoptions.mapper.PurchaseMapper;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.dto.PurchaseDetailDTO;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetailWarranty;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.stockoptions.productoptions.model.Product;
import org.sspd.servicemgmt.stockoptions.productoptions.repository.ProductRepository;
import org.sspd.servicemgmt.stockoptions.productserialoptions.enums.SerialStatus;
import org.sspd.servicemgmt.stockoptions.productserialoptions.model.ProductSerial;
import org.sspd.servicemgmt.stockoptions.productserialoptions.repository.ProductSerialRepository;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.MovementType;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.model.StockMovement;
import org.sspd.servicemgmt.stockoptions.stockmovementoptions.service.StockMovementService;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.sspd.servicemgmt.api.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductSerialRepository serialRepository;
    private final StockMovementService stockMovementService;
    private final JournalWriter journalWriter;
    private final PaymentTransactionService paymentTransactionService;
    private final PurchaseMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final AccountResolver accounts;
    private final PaymentBalanceValidator paymentBalanceValidator;
    private final CompanySettingsService companySettingsService;

    private static final String PURCHASE_TOPIC = "/topic/purchase";

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_CREATE')")
    @Transactional
    public PurchaseDTO save(PurchaseDTO dto) {

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        validateStaffSelection(staff);

        // Duplicate submission guard — same supplier+staff+total within 15 seconds
        BigDecimal estimatedTotal = dto.getDetails().stream()
                .map(d -> d.getUnitCost().multiply(BigDecimal.valueOf(d.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long recentCount = purchaseRepository.countRecentDuplicates(
                dto.getSupplierId(), dto.getStaffId(), estimatedTotal,
                LocalDateTime.now().minusSeconds(15));
        if (recentCount > 0) {
            throw new RuntimeException("Duplicate purchase detected. ထပ်မနှိပ်ပါနှင့် — ခဏ စောင့်ပါ။");
        }

        Purchase purchase = mapper.toEntity(dto);
        purchase.setSupplier(supplier);
        purchase.setStaff(staff);
        purchase.setPurchaseCode("PENDING");

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        List<PurchaseDetail> detailEntities = new ArrayList<>();

        for (PurchaseDetailDTO dDto : dto.getDetails()) {
            Product product = productRepository.findById(dDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            boolean hasSerials = dDto.getSerialNumbers() != null && !dDto.getSerialNumbers().isEmpty();
            if (Boolean.TRUE.equals(product.getHasSerial())) {
                if (!hasSerials)
                    throw new RuntimeException("Serial numbers required for: " + product.getName());
                if (dDto.getSerialNumbers().size() != dDto.getQty())
                    throw new RuntimeException("Serial count must match Qty for: " + product.getName());
            } else if (hasSerials) {
                throw new RuntimeException("Product is non-serial. Purchase without serials, then use manual Assign Serials for: " + product.getName());
            }

            BigDecimal subtotal = dDto.getUnitCost().multiply(BigDecimal.valueOf(dDto.getQty()));
            calculatedTotal = calculatedTotal.add(subtotal);

            detailEntities.add(PurchaseDetail.builder()
                    .purchase(purchase).product(product)
                    .qty(dDto.getQty()).unitCost(dDto.getUnitCost()).subtotal(subtotal)
                    .batchNumber(dDto.getBatchNumber())
                    .expiryDate(dDto.getExpiryDate())
                    .warrantyMonths(dDto.getWarrantyMonths() != null ? dDto.getWarrantyMonths() : 0)
                    .build());

            PurchaseDetail detailEntity = detailEntities.get(detailEntities.size() - 1);
            List<Integer> itemWarranties = normalizeItemWarranties(dDto);
            LocalDate warrantyStart = (dto.getPurchaseDate() != null ? dto.getPurchaseDate() : LocalDateTime.now()).toLocalDate();

            if (hasSerials) {
                updateAverageCost(product, dDto.getUnitCost(), dDto.getQty(),
                        serialRepository.countByProductIdAndStatus(product.getId(), SerialStatus.Available).intValue());
                for (int i = 0; i < dDto.getSerialNumbers().size(); i++) {
                    String sn = dDto.getSerialNumbers().get(i);
                    if (serialRepository.existsBySerialNumber(sn))
                        throw new RuntimeException("Serial '" + sn + "' already exists!");
                    Integer itemWarrantyMonths = itemWarranties.get(i);
                    String serialCondition = (dDto.getSerialConditions() != null && i < dDto.getSerialConditions().size())
                            ? dDto.getSerialConditions().get(i) : null;
                    String serialPhoto = (dDto.getSerialPhotos() != null && i < dDto.getSerialPhotos().size())
                            ? dDto.getSerialPhotos().get(i) : null;
                    serialRepository.save(ProductSerial.builder()
                            .product(product)
                            .serialNumber(sn)
                            .status(SerialStatus.Available)
                            .warrantyMonths(itemWarrantyMonths)
                            .warrantyStartDate(warrantyStart)
                            .warrantyEndDate(warrantyStart.plusMonths(itemWarrantyMonths != null ? itemWarrantyMonths : 0))
                            .condition(serialCondition)
                            .photoBase64(serialPhoto)
                            .build());
                    detailEntity.getWarrantyItems().add(PurchaseDetailWarranty.builder()
                            .purchaseDetail(detailEntity)
                            .itemIndex(i + 1)
                            .serialNumber(sn)
                            .warrantyMonths(itemWarrantyMonths != null ? itemWarrantyMonths : 0)
                            .warrantyStartDate(warrantyStart)
                            .warrantyEndDate(warrantyStart.plusMonths(itemWarrantyMonths != null ? itemWarrantyMonths : 0))
                            .build());
                }
            } else if (!Boolean.TRUE.equals(product.getHasSerial())) {
                int current = product.getStockQty() != null ? product.getStockQty() : 0;
                updateAverageCost(product, dDto.getUnitCost(), dDto.getQty(), current);
                product.setStockQty(current + dDto.getQty());
                productRepository.save(product);
                for (int i = 0; i < dDto.getQty(); i++) {
                    Integer itemWarrantyMonths = itemWarranties.get(i);
                    detailEntity.getWarrantyItems().add(PurchaseDetailWarranty.builder()
                            .purchaseDetail(detailEntity)
                            .itemIndex(i + 1)
                            .serialNumber(null)
                            .warrantyMonths(itemWarrantyMonths != null ? itemWarrantyMonths : 0)
                            .warrantyStartDate(warrantyStart)
                            .warrantyEndDate(warrantyStart.plusMonths(itemWarrantyMonths != null ? itemWarrantyMonths : 0))
                            .build());
                }
            }

            stockMovementService.recordMovement(StockMovement.builder()
                    .product(product).movementType(MovementType.IN).qty(dDto.getQty())
                    .referenceType("Purchase").build());
        }

        purchase.setDetails(detailEntities);
        purchase.setTotalAmount(calculatedTotal);
        BigDecimal discountAmount = dto.getDiscountAmount() != null ? dto.getDiscountAmount() : BigDecimal.ZERO;
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Discount amount cannot be negative.");
        }
        if (discountAmount.compareTo(calculatedTotal) > 0) {
            throw new RuntimeException("Discount amount cannot exceed purchase total.");
        }
        BigDecimal netAmount = calculatedTotal.subtract(discountAmount);
        purchase.setDiscountAmount(discountAmount);
        purchase.setPaidAmount(paymentTotal(dto.getPayments(), dto.getPaidAmount()));
        if (purchase.getPaidAmount().compareTo(netAmount) > 0) {
            throw new RuntimeException("Paid amount cannot exceed net purchase amount.");
        }
        purchase.setReturnAmount(BigDecimal.ZERO);
        purchase.setRefundAmount(BigDecimal.ZERO);
        purchase.setNetAmount(netAmount);
        purchase.setSupplierCreditAmount(BigDecimal.ZERO);
        purchase.setDueAmount(netAmount.subtract(purchase.getPaidAmount()));

        if (purchase.getDueAmount().compareTo(BigDecimal.ZERO) <= 0)
            purchase.setPaymentStatus(PaymentStatus.Paid);
        else if (purchase.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
            purchase.setPaymentStatus(PaymentStatus.Partial);
        else
            purchase.setPaymentStatus(PaymentStatus.Pending);

        if (purchase.getPurchaseDate() == null)
            purchase.setPurchaseDate(LocalDateTime.now());
        purchase.setDueDate(resolveDueDate(dto, purchase));

        Purchase savedPurchase = purchaseRepository.save(purchase);
        syncSupplierBalance(supplier);
        savedPurchase.setPurchaseCode(generatePurchaseCode(savedPurchase.getId()));
        savedPurchase = purchaseRepository.save(savedPurchase);

        createPurchasePaymentTransactions(savedPurchase, dto);

        // ✅ Journal Entry — Periodic System
        createPurchaseJournal(savedPurchase, dto);

        messagingTemplate.convertAndSend(PURCHASE_TOPIC, "PURCHASE_CREATED");
        return enrichWarrantyItems(mapper.toDto(savedPurchase), savedPurchase);
    }

    private void validateStaffSelection(Staff selectedStaff) {
        if (hasAuthority("CAN_ACCESS_PURCHASE_STAFF_OVERRIDE")) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        User user = username == null ? null : userRepository.findByUsernameOrEmail(username, username).orElse(null);
        boolean matches = user != null && user.getStaff() != null
                && user.getStaff().getId().equals(selectedStaff.getId());
        if (!matches) {
            throw new AccessDeniedException("Purchase အတွက် သင့် Staff ကိုသာ ရွေးချယ်နိုင်ပါသည်။");
        }
    }

    private boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private void updateAverageCost(Product product, BigDecimal purchaseUnitCost, Integer purchasedQty, int currentQty) {
        int qty = purchasedQty != null ? purchasedQty : 0;
        if (qty <= 0 || purchaseUnitCost == null) return;
        BigDecimal oldCost = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        BigDecimal oldValue = oldCost.multiply(BigDecimal.valueOf(Math.max(0, currentQty)));
        BigDecimal newValue = purchaseUnitCost.multiply(BigDecimal.valueOf(qty));
        BigDecimal average = oldValue.add(newValue)
                .divide(BigDecimal.valueOf(Math.max(1, currentQty + qty)), 2, java.math.RoundingMode.HALF_UP);
        product.setCostPrice(average);
    }

    /**
     * ✅ Purchase Journal — Periodic System
     *
     * Case 1: ငွေသားချက်ချင်း
     *   DR: Purchases (EXP-007)      totalAmount
     *   CR: Cash/Bank/KPay           totalAmount
     *
     * Case 2: အကြွေး (Credit Purchase)
     *   DR: Purchases (EXP-007)      totalAmount
     *   CR: Accounts Payable (LIA-002)  totalAmount
     *
     * Case 3: Partial Payment
     *   DR: Purchases (EXP-007)      totalAmount
     *   CR: Accounts Payable (LIA-002)  dueAmount
     *   CR: Cash/Bank/KPay              paidAmount
     */
    private void createPurchaseJournal(Purchase p, PurchaseDTO dto) {
        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(p.getPurchaseCode());
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Purchase from: " + p.getSupplier().getName());
        journalDTO.setStaffId(p.getStaff().getId());

        List<JournalDetailDTO> details = new ArrayList<>();

        // ✅ DR: Purchases — Periodic system တွင် ဝယ်သောအခါ Purchases account DR
        JournalDetailDTO drPurchases = new JournalDetailDTO();
        drPurchases.setAccountId(accounts.purchases().getId());  // EXP-007, id=20
        drPurchases.setDebit(p.getNetAmount() != null ? p.getNetAmount() : p.getTotalAmount());
        drPurchases.setCredit(BigDecimal.ZERO);
        details.add(drPurchases);

        // ✅ CR: Accounts Payable — အကြွေးကျန်လျှင်
        if (p.getDueAmount().compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO crPayable = new JournalDetailDTO();
            crPayable.setAccountId(accounts.payable().getId());  // LIA-002, id=8
            crPayable.setDebit(BigDecimal.ZERO);
            crPayable.setCredit(p.getDueAmount());
            details.add(crPayable);
        }

        // ✅ CR: Cash/Bank/KPay/WavePay — လက်ငင်းပေးချေမှုရှိလျှင်
        if (p.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            if (dto.getPayments() != null && !dto.getPayments().isEmpty()) {
                for (PaymentLine line : resolvePaymentLines(dto.getPayments(), p.getPaidAmount(), dto.getPaymentMethodId(), true)) {
                    JournalDetailDTO crPayment = new JournalDetailDTO();
                    crPayment.setAccountId(line.method().getAccount().getId());
                    crPayment.setDebit(BigDecimal.ZERO);
                    crPayment.setCredit(line.amount());
                    details.add(crPayment);
                }
            } else {
            PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            JournalDetailDTO crPayment = new JournalDetailDTO();
            crPayment.setAccountId(method.getAccount().getId()); // ASS-002/003/006/007 အလိုအလျောက်
            crPayment.setDebit(BigDecimal.ZERO);
            crPayment.setCredit(p.getPaidAmount());
            details.add(crPayment);
            }
        }

        journalDTO.setDetails(details);
        journalWriter.write(journalDTO);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public PaymentTransactionDTO payPurchaseDebt(PaymentTransactionDTO dto) {

        Purchase purchase = purchaseRepository.findById(dto.getReferenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        PaymentMethod method = paymentMethodRepository.findById(dto.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));

        BigDecimal payingAmount = dto.getAmount();
        paymentBalanceValidator.validateSufficientBalance(method, payingAmount);

        if (payingAmount.compareTo(purchase.getDueAmount()) > 0)
            throw new RuntimeException("Paying amount exceeds due! Due: " + purchase.getDueAmount());

        purchase.setPaidAmount(purchase.getPaidAmount().add(payingAmount));
        purchase.setDueAmount(purchase.getDueAmount().subtract(payingAmount));

        if (purchase.getDueAmount().compareTo(BigDecimal.ZERO) <= 0)
            purchase.setPaymentStatus(PaymentStatus.Paid);
        else
            purchase.setPaymentStatus(PaymentStatus.Partial);
        purchaseRepository.save(purchase);

        Supplier supplier = purchase.getSupplier();
        syncSupplierBalance(supplier);

        PaymentTransaction paymentTx = new PaymentTransaction();
        paymentTx.setReferenceId(purchase.getId());
        paymentTx.setReferenceType(ReferenceType.Purchase);
        paymentTx.setPaymentMethod(method);
        paymentTx.setAmount(payingAmount);
        paymentTx.setPaymentDate(LocalDateTime.now());
        String txnNo = (dto.getTransactionNo() == null || dto.getTransactionNo().isEmpty())
                ? generateTransactionNo() : dto.getTransactionNo();
        paymentTx.setTransactionNo(txnNo);
        PaymentTransaction savedEntity = paymentTransactionRepository.save(paymentTx);

        // ✅ Debt Payment Journal
        createDebtPaymentJournal(savedEntity, supplier.getName(), purchase.getStaff().getId());

        messagingTemplate.convertAndSend("/topic/payment-transaction", "DEBT_PAID");
        return mapper.toDto(savedEntity);
    }

    /**
     * ✅ Debt Payment Journal
     *
     * DR: Accounts Payable (LIA-002) — အကြွေးလျော့
     * CR: Cash/Bank/KPay             — ပိုင်ဆိုင်မှုလျော့
     */
    private void createDebtPaymentJournal(PaymentTransaction tx, String supplierName, Integer staffId) {
        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo(tx.getTransactionNo());
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Debt Payment to Supplier: " + supplierName);
        journalDTO.setStaffId(staffId);

        List<JournalDetailDTO> details = new ArrayList<>();

        // ✅ DR: Accounts Payable
        JournalDetailDTO drPayable = new JournalDetailDTO();
        drPayable.setAccountId(accounts.payable().getId());  // LIA-002, id=8
        drPayable.setDebit(tx.getAmount());
        drPayable.setCredit(BigDecimal.ZERO);
        details.add(drPayable);

        // ✅ CR: Cash/Bank/KPay — method.account မှ အလိုအလျောက်
        JournalDetailDTO crPayment = new JournalDetailDTO();
        crPayment.setAccountId(tx.getPaymentMethod().getAccount().getId());
        crPayment.setDebit(BigDecimal.ZERO);
        crPayment.setCredit(tx.getAmount());
        details.add(crPayment);

        journalDTO.setDetails(details);
        journalWriter.write(journalDTO);
    }

    // ── Helper Methods ───────────────────────────────────────────

    private void syncSupplierBalance(Supplier supplier) {
        BigDecimal totalDue = purchaseRepository.sumDueAmountBySupplierId(supplier.getId());
        if (totalDue == null) totalDue = BigDecimal.ZERO;
        BigDecimal supplierCredit = purchaseRepository.sumSupplierCreditAmountBySupplierId(supplier.getId());
        if (supplierCredit == null) supplierCredit = BigDecimal.ZERO;
        BigDecimal opening = supplier.getOpeningBalance() != null ? supplier.getOpeningBalance() : BigDecimal.ZERO;
        supplier.setCurrentBalance(opening.add(totalDue).subtract(supplierCredit));
        supplierRepository.save(supplier);
    }

    private String generatePurchaseCode(Integer id) {
        var cfg = companySettingsService.getSettings();
        String prefix = cfg.getPurchasePrefix() != null && !cfg.getPurchasePrefix().isBlank() ? cfg.getPurchasePrefix() : "PUR";
        int digits = cfg.getPurchaseDigits() != null ? cfg.getPurchaseDigits() : 5;
        return String.format("%s-%0" + digits + "d", prefix, id);
    }

    private String generateTransactionNo() {
        Long count = paymentTransactionRepository.count();
        return String.format("TXN-%06d", count + 1);
    }

    private void createPurchasePaymentTransactions(Purchase purchase, PurchaseDTO dto) {
        BigDecimal paid = purchase.getPaidAmount() != null ? purchase.getPaidAmount() : BigDecimal.ZERO;
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return;

        for (PaymentLine line : resolvePaymentLines(dto.getPayments(), paid, dto.getPaymentMethodId(), true)) {
            paymentBalanceValidator.validateSufficientBalance(line.method(), line.amount());

            PaymentTransaction paymentTx = new PaymentTransaction();
            paymentTx.setReferenceId(purchase.getId());
            paymentTx.setReferenceType(ReferenceType.Purchase);
            paymentTx.setPaymentMethod(line.method());
            paymentTx.setAmount(line.amount());
            paymentTx.setPaymentDate(LocalDateTime.now());
            paymentTx.setTransactionNo(line.transactionNo() == null || line.transactionNo().isBlank()
                    ? generateTransactionNo()
                    : line.transactionNo());
            paymentTransactionRepository.save(paymentTx);
        }
    }

    private BigDecimal paymentTotal(List<PaymentTransactionDTO> payments, BigDecimal fallback) {
        if (payments == null || payments.isEmpty()) return fallback != null ? fallback : BigDecimal.ZERO;
        return payments.stream()
                .map(PaymentTransactionDTO::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PaymentLine> resolvePaymentLines(List<PaymentTransactionDTO> payments, BigDecimal expectedTotal,
                                                  Integer fallbackMethodId, boolean requireMethod) {
        List<PaymentTransactionDTO> source = payments;
        if (source == null || source.isEmpty()) {
            PaymentTransactionDTO fallback = new PaymentTransactionDTO();
            fallback.setPaymentMethodId(fallbackMethodId);
            fallback.setAmount(expectedTotal);
            source = List.of(fallback);
        }

        BigDecimal total = paymentTotal(source, BigDecimal.ZERO);
        if (expectedTotal != null && total.compareTo(expectedTotal) != 0) {
            throw new RuntimeException("Split payment total must equal paid amount.");
        }

        List<PaymentLine> lines = new ArrayList<>();
        for (PaymentTransactionDTO payment : source) {
            BigDecimal amount = payment.getAmount() != null ? payment.getAmount() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            Integer methodId = payment.getPaymentMethodId() != null ? payment.getPaymentMethodId() : fallbackMethodId;
            if (methodId == null && requireMethod) throw new RuntimeException("Payment Method is required for each payment line.");
            PaymentMethod method = paymentMethodRepository.findById(methodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
            lines.add(new PaymentLine(method, amount, payment.getTransactionNo()));
        }
        return lines;
    }

    private record PaymentLine(PaymentMethod method, BigDecimal amount, String transactionNo) {}

    @Transactional(readOnly = true)
    public PageResponse<PurchaseDTO> findAll(String search, String dateFrom, String dateTo, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        LocalDateTime from = parseDate(dateFrom, false);
        LocalDateTime to = parseDate(dateTo, true);
        return PageResponse.of(purchaseRepository.findBySearchAndDateRange(search, from, to, pageable)
                .map(entity -> enrichWarrantyItems(mapper.toDto(entity), entity)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getStats(String dateFrom, String dateTo) {
        LocalDateTime from = parseDate(dateFrom, false);
        LocalDateTime to = parseDate(dateTo, true);
        List<Object[]> rows = purchaseRepository.findStatsByDateRange(from, to);
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        if (!rows.isEmpty()) {
            Object[] row = rows.get(0);
            stats.put("count", row[0]);
            stats.put("totalAmount", row[1]);
            stats.put("paidAmount", row[2]);
            stats.put("dueAmount", row[3]);
        } else {
            stats.put("count", 0L);
            stats.put("totalAmount", BigDecimal.ZERO);
            stats.put("paidAmount", BigDecimal.ZERO);
            stats.put("dueAmount", BigDecimal.ZERO);
        }
        return stats;
    }

    private LocalDateTime parseDate(String date, boolean endOfDay) {
        if (date == null || date.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(date);
            return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public PurchaseDTO findById(Integer id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        return enrichWarrantyItems(mapper.toDto(purchase), purchase);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_UPDATE')")
    @Transactional
    public PurchaseDTO update(Integer id, PurchaseDTO dto) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));

        Supplier oldSupplier = purchase.getSupplier();
        BigDecimal oldDue = purchase.getDueAmount() != null ? purchase.getDueAmount() : BigDecimal.ZERO;

        if (dto.getSupplierId() != null &&
                (oldSupplier == null || !dto.getSupplierId().equals(oldSupplier.getId()))) {
            Supplier newSupplier = supplierRepository.findById(dto.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
            purchase.setSupplier(newSupplier);
        }

        if (dto.getStaffId() != null) {
            Staff staff = staffRepository.findById(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
            purchase.setStaff(staff);
        }

        if (dto.getPurchaseDate() != null) purchase.setPurchaseDate(dto.getPurchaseDate());
        if (dto.getDueDate() != null) purchase.setDueDate(dto.getDueDate());
        if (dto.getRemark() != null) purchase.setRemark(dto.getRemark());

        if (dto.getDetails() != null && !dto.getDetails().isEmpty())
            throw new RuntimeException("Detail update not supported. Use purchase return or cancel & recreate.");

        if (dto.getPaidAmount() != null) {
            purchase.setPaidAmount(dto.getPaidAmount());
        }

        BigDecimal totalAmount = purchase.getNetAmount() != null ? purchase.getNetAmount() :
                (purchase.getTotalAmount() != null ? purchase.getTotalAmount() : BigDecimal.ZERO);
        BigDecimal paidAmount = purchase.getPaidAmount() != null ? purchase.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newDue = totalAmount.subtract(paidAmount);
        BigDecimal supplierCredit = BigDecimal.ZERO;
        if (newDue.compareTo(BigDecimal.ZERO) < 0) {
            supplierCredit = newDue.abs().subtract(purchase.getRefundAmount() != null ? purchase.getRefundAmount() : BigDecimal.ZERO);
            if (supplierCredit.compareTo(BigDecimal.ZERO) < 0) supplierCredit = BigDecimal.ZERO;
            newDue = BigDecimal.ZERO;
        }
        purchase.setDueAmount(newDue);
        purchase.setSupplierCreditAmount(supplierCredit);
        if (newDue.compareTo(BigDecimal.ZERO) > 0 && purchase.getDueDate() == null) {
            LocalDate baseDate = purchase.getPurchaseDate() != null
                    ? purchase.getPurchaseDate().toLocalDate()
                    : LocalDate.now();
            purchase.setDueDate(baseDate.plusDays(30));
        }

        if (newDue.compareTo(BigDecimal.ZERO) <= 0)
            purchase.setPaymentStatus(PaymentStatus.Paid);
        else if (paidAmount.compareTo(BigDecimal.ZERO) > 0)
            purchase.setPaymentStatus(PaymentStatus.Partial);
        else
            purchase.setPaymentStatus(PaymentStatus.Pending);

        Purchase savedPurchase = purchaseRepository.save(purchase);

        Supplier newSupplier = savedPurchase.getSupplier();
        if (oldSupplier != null) {
            syncSupplierBalance(oldSupplier);
        }
        if (newSupplier != null && (oldSupplier == null || !newSupplier.getId().equals(oldSupplier.getId()))) {
            syncSupplierBalance(newSupplier);
        }

        messagingTemplate.convertAndSend(PURCHASE_TOPIC, "PURCHASE_UPDATED");
        return enrichWarrantyItems(mapper.toDto(savedPurchase), savedPurchase);
    }

    private List<Integer> normalizeItemWarranties(PurchaseDetailDTO dDto) {
        int qty = dDto.getQty() != null ? dDto.getQty() : 0;
        int bulkMonths = dDto.getWarrantyMonths() != null ? dDto.getWarrantyMonths() : 0;
        if (bulkMonths < 0) {
            throw new RuntimeException("Warranty months cannot be negative");
        }
        if (qty <= 0) {
            throw new RuntimeException("Qty must be greater than zero");
        }
        List<Integer> raw = dDto.getItemWarranties();
        if (raw == null || raw.isEmpty()) {
            return java.util.stream.IntStream.range(0, qty)
                    .mapToObj(i -> bulkMonths)
                    .toList();
        }
        if (raw.size() != qty) {
            throw new RuntimeException("Item warranties count must match qty");
        }
        return raw.stream().map(m -> {
            int months = m != null ? m : bulkMonths;
            if (months < 0) throw new RuntimeException("Warranty months cannot be negative");
            return months;
        }).toList();
    }

    private LocalDate resolveDueDate(PurchaseDTO dto, Purchase purchase) {
        if (dto.getDueDate() != null) return dto.getDueDate();
        if (purchase.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) return null;
        LocalDate baseDate = purchase.getPurchaseDate() != null
                ? purchase.getPurchaseDate().toLocalDate()
                : LocalDate.now();
        return baseDate.plusDays(30);
    }

    private PurchaseDTO enrichWarrantyItems(PurchaseDTO dto, Purchase purchase) {
        if (dto.getDetails() == null || purchase.getDetails() == null) return dto;
        for (PurchaseDetailDTO detailDTO : dto.getDetails()) {
            if (detailDTO.getId() == null) continue;
            PurchaseDetail entityDetail = purchase.getDetails().stream()
                    .filter(d -> detailDTO.getId().equals(d.getId()))
                    .findFirst()
                    .orElse(null);
            if (entityDetail == null || entityDetail.getWarrantyItems() == null) continue;
            List<Integer> months = entityDetail.getWarrantyItems().stream()
                    .sorted(Comparator.comparing(PurchaseDetailWarranty::getItemIndex))
                    .map(PurchaseDetailWarranty::getWarrantyMonths)
                    .toList();
            detailDTO.setItemWarranties(months);
            List<String> serials = entityDetail.getWarrantyItems().stream()
                    .sorted(Comparator.comparing(PurchaseDetailWarranty::getItemIndex))
                    .map(PurchaseDetailWarranty::getSerialNumber)
                    .filter(sn -> sn != null && !sn.isBlank())
                    .toList();
            detailDTO.setSerialNumbers(serials);
        }
        return dto;
    }
}
