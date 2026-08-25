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
import org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus;
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
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.sspd.servicemgmt.api.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final CashDrawerService cashDrawerService;
    private final org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository.PurchaseReturnRepository purchaseReturnRepository;
    private final org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository.PurchaseOrderRepository purchaseOrderRepository;
    private final org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard periodGuard;
    private final org.sspd.servicemgmt.purchaseoptions.budget.service.PurchaseBudgetService purchaseBudgetService;
    private final org.sspd.servicemgmt.stockoptions.lotoptions.service.StockLotService stockLotService;

    private static final String PURCHASE_TOPIC = "/topic/purchase";

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_CREATE')")
    @Transactional
    public PurchaseDTO save(PurchaseDTO dto) {

        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        Staff staff = staffRepository.findById(dto.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        validateStaffSelection(staff);

        boolean draft = PurchaseStatus.DRAFT.name().equalsIgnoreCase(dto.getStatus());
        if (!draft) periodGuard.assertOpen(dto.getPurchaseDate(), "create purchase");
        validateSupplierInvoiceNumber(dto.getSupplierId(), dto.getSupplierInvoiceNo(), null);

        // Duplicate submission guard — same supplier+staff+total within 15 seconds (confirmed only)
        if (!draft) {
            BigDecimal estimatedTotal = dto.getDetails().stream()
                    .map(d -> d.getUnitCost().multiply(BigDecimal.valueOf(d.getQty())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long recentCount = purchaseRepository.countRecentDuplicates(
                    dto.getSupplierId(), dto.getStaffId(), estimatedTotal,
                    LocalDateTime.now().minusSeconds(15));
            if (recentCount > 0) {
                throw new RuntimeException("Duplicate purchase detected. ထပ်မနှိပ်ပါနှင့် — ခဏ စောင့်ပါ။");
            }
        }

        Purchase purchase = mapper.toEntity(dto);
        purchase.setSupplier(supplier);
        purchase.setStaff(staff);
        purchase.setPurchaseCode("PENDING");
        purchase.setStatus(draft ? PurchaseStatus.DRAFT : PurchaseStatus.CONFIRMED);
        applyAttachment(purchase, dto);

        validateTaxAndCharges(dto);
        java.util.List<String> budgetWarnings = java.util.List.of();
        if (!draft) budgetWarnings = purchaseBudgetService.validate((dto.getPurchaseDate()!=null?dto.getPurchaseDate():LocalDateTime.now()).toLocalDate(), dto.getDetails());

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        List<PurchaseDetail> detailEntities = new ArrayList<>();

        for (PurchaseDetailDTO dDto : dto.getDetails()) {
            Product product = productRepository.findById(dDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            boolean hasSerials = dDto.getSerialNumbers() != null && !dDto.getSerialNumbers().isEmpty();
            if (!draft) {
                if (Boolean.TRUE.equals(product.getHasSerial())) {
                    if (!hasSerials)
                        throw new RuntimeException("Serial numbers required for: " + product.getName());
                    if (dDto.getSerialNumbers().size() != dDto.getQty())
                        throw new RuntimeException("Serial count must match Qty for: " + product.getName());
                } else if (hasSerials) {
                    throw new RuntimeException("Product is non-serial. Purchase without serials, then use manual Assign Serials for: " + product.getName());
                }
            } else {
                if (dDto.getQty() == null || dDto.getQty() <= 0)
                    throw new RuntimeException("Qty must be greater than zero for draft: " + product.getName());
            }

            BigDecimal subtotal = dDto.getUnitCost().multiply(BigDecimal.valueOf(dDto.getQty()));
            calculatedTotal = calculatedTotal.add(subtotal);

            BigDecimal allocatedLandedCost = allocatedOtherCharge(dDto.getUnitCost(), dDto.getQty(),
                    dDto.getAllocatedLandedCost(), dto);
            detailEntities.add(PurchaseDetail.builder()
                    .purchase(purchase).product(product)
                    .qty(dDto.getQty()).unitCost(dDto.getUnitCost()).subtotal(subtotal)
                    .allocatedLandedCost(allocatedLandedCost)
                    .batchNumber(dDto.getBatchNumber())
                    .expiryDate(dDto.getExpiryDate())
                    .warrantyMonths(dDto.getWarrantyMonths() != null ? dDto.getWarrantyMonths() : 0)
                    .build());

            if (draft) continue;  // Draft → stock/serial/accounting side effects deferred until confirm

            PurchaseDetail detailEntity = detailEntities.get(detailEntities.size() - 1);
            List<Integer> itemWarranties = normalizeItemWarranties(dDto);
            LocalDate warrantyStart = (dto.getPurchaseDate() != null ? dto.getPurchaseDate() : LocalDateTime.now()).toLocalDate();

            if (hasSerials) {
                updateAverageCost(product, effectiveUnitCost(dDto.getUnitCost(), dDto.getQty(), allocatedLandedCost, dto), dDto.getQty(),
                        serialRepository.countByProductIdAndStatus(product.getId(), SerialStatus.Available).intValue());
                if (dDto.getUnitCost() != null && dDto.getUnitCost().compareTo(BigDecimal.ZERO) > 0)
                    product.setLastPurchaseCost(dDto.getUnitCost());
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
                updateAverageCost(product, effectiveUnitCost(dDto.getUnitCost(), dDto.getQty(), allocatedLandedCost, dto), dDto.getQty(), current);
                if (dDto.getUnitCost() != null && dDto.getUnitCost().compareTo(BigDecimal.ZERO) > 0)
                    product.setLastPurchaseCost(dDto.getUnitCost());
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
                    .referenceType("Purchase").warehouseName(dto.getWarehouseName()).build());
        }

        if (detailEntities.isEmpty()) {
            throw new RuntimeException("Purchase must have at least one detail line.");
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
        BigDecimal taxAmount = safe(dto.getTaxAmount());
        BigDecimal otherCharges = safe(dto.getOtherCharges());
        BigDecimal netAmount = calculatePayableNet(calculatedTotal, discountAmount, dto);
        purchase.setDiscountAmount(discountAmount);
        purchase.setTaxAmount(taxAmount);
        purchase.setOtherCharges(otherCharges);
        purchase.setTaxMode(normalizeTaxMode(dto.getTaxMode()));
        purchase.setTaxRate(safe(dto.getTaxRate()));
        purchase.setWithholdingTaxAmount(safe(dto.getWithholdingTaxAmount()));
        purchase.setLandedCostAllocationMethod(normalizeAllocationMethod(dto.getLandedCostAllocationMethod()));
        purchase.setWarehouseName(dto.getWarehouseName());
        applyCurrencySnapshot(purchase, dto, netAmount);

        if (draft) {
            purchase.setPaidAmount(BigDecimal.ZERO);
            purchase.setReturnAmount(BigDecimal.ZERO);
            purchase.setRefundAmount(BigDecimal.ZERO);
            purchase.setNetAmount(netAmount);
            purchase.setSupplierCreditAmount(BigDecimal.ZERO);
            purchase.setDueAmount(netAmount);
            purchase.setPaymentStatus(PaymentStatus.Pending);
            if (purchase.getPurchaseDate() == null)
                purchase.setPurchaseDate(LocalDateTime.now());

            Purchase savedDraft = purchaseRepository.save(purchase);
            savedDraft.setPurchaseCode(generatePurchaseCode(savedDraft.getId()));
            savedDraft = purchaseRepository.save(savedDraft);
            messagingTemplate.convertAndSend(PURCHASE_TOPIC, "PURCHASE_DRAFT_CREATED");
            return withBudgetWarnings(enrichWarrantyItems(mapper.toDto(savedDraft), savedDraft), budgetWarnings);
        }

        purchase.setPaidAmount(paymentTotal(dto.getPayments(), dto.getPaidAmount()));
        if (purchase.getPaidAmount().compareTo(netAmount) > 0) {
            throw new RuntimeException("Paid amount cannot exceed net purchase amount.");
        }
        purchase.setReturnAmount(BigDecimal.ZERO);
        purchase.setRefundAmount(BigDecimal.ZERO);
        purchase.setNetAmount(netAmount);
        purchase.setSupplierCreditAmount(BigDecimal.ZERO);
        purchase.setDueAmount(netAmount.subtract(purchase.getPaidAmount()));
        applyAndValidateSupplierCreditOverride(purchase, supplier, purchase.getDueAmount(), dto);

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
        stockLotService.receivePurchase(savedPurchase);

        createPurchasePaymentTransactions(savedPurchase, dto);

        // ✅ Journal Entry — Periodic System
        createPurchaseJournal(savedPurchase, dto);

        messagingTemplate.convertAndSend(PURCHASE_TOPIC, "PURCHASE_CREATED");
        return withBudgetWarnings(enrichWarrantyItems(mapper.toDto(savedPurchase), savedPurchase), budgetWarnings);
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
        BigDecimal tax = safe(p.getTaxAmount()).min(safe(p.getNetAmount()));
        BigDecimal withholding = safe(p.getWithholdingTaxAmount());
        BigDecimal grossBeforeWithholding = safe(p.getNetAmount()).add(withholding);
        BigDecimal purchaseCost = grossBeforeWithholding.subtract(tax);
        JournalDetailDTO drPurchases = new JournalDetailDTO();
        drPurchases.setAccountId(accounts.purchases().getId());  // EXP-007, id=20
        drPurchases.setDebit(purchaseCost);
        drPurchases.setCredit(BigDecimal.ZERO);
        if (purchaseCost.signum() > 0) details.add(drPurchases);
        if (tax.signum() > 0) {
            JournalDetailDTO drInputVat = new JournalDetailDTO();
            drInputVat.setAccountId(accounts.inputTaxReceivable().getId());
            drInputVat.setDebit(tax);
            drInputVat.setCredit(BigDecimal.ZERO);
            details.add(drInputVat);
        }
        if (withholding.signum() > 0) {
            JournalDetailDTO crWithholding = new JournalDetailDTO();
            crWithholding.setAccountId(accounts.withholdingTaxPayable().getId());
            crWithholding.setDebit(BigDecimal.ZERO);
            crWithholding.setCredit(withholding);
            details.add(crWithholding);
        }

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
        periodGuard.assertOpen(LocalDateTime.now(), "record purchase debt payment");
        if (purchase.isCancelled() || purchase.isDraft())
            throw new IllegalStateException("Only confirmed purchases can receive debt payments.");
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
        if (isCashMethod(method))
            cashDrawerService.recordPurchaseCashOut(payingAmount, "Purchase debt payment " + purchase.getPurchaseCode());

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

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void applyAttachment(Purchase purchase, PurchaseDTO dto) {
        if (dto.getAttachmentName() != null || dto.getAttachmentData() != null) {
            purchase.setAttachmentName(dto.getAttachmentName());
            purchase.setAttachmentData(dto.getAttachmentData());
        }
    }

    private void validateTaxAndCharges(PurchaseDTO dto) {
        if (safe(dto.getTaxAmount()).compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("Tax amount cannot be negative.");
        if (safe(dto.getOtherCharges()).compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("Other charges cannot be negative.");
        if (safe(dto.getWithholdingTaxAmount()).compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("Withholding tax cannot be negative.");
        if (safe(dto.getTaxRate()).compareTo(BigDecimal.ZERO) < 0)
            throw new RuntimeException("Tax rate cannot be negative.");
        String method = normalizeAllocationMethod(dto.getLandedCostAllocationMethod());
        if ("MANUAL".equals(method)) {
            BigDecimal allocated = dto.getDetails() == null ? BigDecimal.ZERO : dto.getDetails().stream()
                    .map(PurchaseDetailDTO::getAllocatedLandedCost).map(this::safe)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocated.subtract(safe(dto.getOtherCharges())).abs().compareTo(new BigDecimal("0.01")) > 0)
                throw new RuntimeException("Manual landed-cost allocations must equal other charges.");
        }
    }

    private void validateSupplierInvoiceNumber(Integer supplierId, String invoiceNo, Integer excludeId) {
        if (invoiceNo == null || invoiceNo.isBlank()) return;
        String normalized = invoiceNo.trim();
        if (purchaseRepository.countSupplierInvoiceDuplicates(supplierId, normalized, excludeId) > 0) {
            throw new RuntimeException("Supplier invoice number already exists for this supplier: " + normalized);
        }
    }

    /**
     * Landing-cost aware unit cost — allocates discount (negative share), tax and
     * other charges proportionally over the line subtotal so average cost reflects
     * the true landed cost of each item.
     */
    private BigDecimal effectiveUnitCost(BigDecimal unitCost, Integer qty, BigDecimal allocatedCharge, PurchaseDTO dto) {
        if (unitCost == null) return BigDecimal.ZERO;
        BigDecimal subtotal = dto.getDetails().stream()
                .map(d -> safe(d.getUnitCost()).multiply(BigDecimal.valueOf(d.getQty() != null ? d.getQty() : 0)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) return unitCost;
        BigDecimal lineSubtotal = unitCost.multiply(BigDecimal.valueOf(qty != null ? qty : 0));
        BigDecimal discountShare = safe(dto.getDiscountAmount()).multiply(lineSubtotal)
                .divide(subtotal, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal lineTrueCost = lineSubtotal.subtract(discountShare).add(safe(allocatedCharge));
        return lineTrueCost.divide(BigDecimal.valueOf(Math.max(1, qty != null ? qty : 0)), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal allocatedOtherCharge(BigDecimal unitCost, Integer qty, BigDecimal manual, PurchaseDTO dto) {
        BigDecimal charges = safe(dto.getOtherCharges());
        if (charges.signum() == 0) return BigDecimal.ZERO;
        String method = normalizeAllocationMethod(dto.getLandedCostAllocationMethod());
        if ("MANUAL".equals(method)) return safe(manual);
        if ("QUANTITY".equals(method)) {
            int totalQty = dto.getDetails().stream().mapToInt(d -> d.getQty() != null ? d.getQty() : 0).sum();
            return totalQty <= 0 ? BigDecimal.ZERO : charges.multiply(BigDecimal.valueOf(qty != null ? qty : 0))
                    .divide(BigDecimal.valueOf(totalQty), 6, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal total = dto.getDetails().stream().map(d -> safe(d.getUnitCost())
                .multiply(BigDecimal.valueOf(d.getQty() != null ? d.getQty() : 0))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal line = safe(unitCost).multiply(BigDecimal.valueOf(qty != null ? qty : 0));
        return total.signum() == 0 ? BigDecimal.ZERO : charges.multiply(line).divide(total, 6, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calculatePayableNet(BigDecimal total, BigDecimal discount, PurchaseDTO dto) {
        BigDecimal gross = total.subtract(discount).add(safe(dto.getOtherCharges()));
        if (!"INCLUSIVE".equals(normalizeTaxMode(dto.getTaxMode()))) gross = gross.add(safe(dto.getTaxAmount()));
        BigDecimal net = gross.subtract(safe(dto.getWithholdingTaxAmount()));
        if (net.signum() < 0) throw new RuntimeException("Withholding tax cannot exceed payable total.");
        return net;
    }
    private String normalizeTaxMode(String mode) { return "INCLUSIVE".equalsIgnoreCase(mode) ? "INCLUSIVE" : "EXCLUSIVE"; }
    private String normalizeAllocationMethod(String method) {
        if ("QUANTITY".equalsIgnoreCase(method)) return "QUANTITY";
        if ("MANUAL".equalsIgnoreCase(method)) return "MANUAL";
        return "VALUE";
    }

    private void applyCurrencySnapshot(Purchase purchase, PurchaseDTO dto, BigDecimal baseNetAmount) {
        String currency = dto.getCurrencyCode() == null || dto.getCurrencyCode().isBlank()
                ? "MMK" : dto.getCurrencyCode().trim().toUpperCase();
        if (currency.length() != 3) throw new RuntimeException("Currency code must be a 3-letter ISO code.");
        BigDecimal rate = "MMK".equals(currency) ? BigDecimal.ONE : safe(dto.getExchangeRate());
        if (rate.compareTo(BigDecimal.ZERO) <= 0) throw new RuntimeException("Exchange rate must be greater than zero.");
        BigDecimal expectedForeign = baseNetAmount.divide(rate, 2, java.math.RoundingMode.HALF_UP);
        BigDecimal foreign = dto.getForeignNetAmount() != null ? dto.getForeignNetAmount() : expectedForeign;
        BigDecimal converted = foreign.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
        if (foreign.signum() < 0 || converted.subtract(baseNetAmount).abs().compareTo(new BigDecimal("1.00")) > 0)
            throw new RuntimeException("Foreign amount x exchange rate must match the MMK payable total.");
        purchase.setCurrencyCode(currency);
        purchase.setExchangeRate(rate);
        purchase.setForeignNetAmount(foreign);
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
            if (isCashMethod(line.method()))
                cashDrawerService.recordPurchaseCashOut(line.amount(), "Purchase payment " + purchase.getPurchaseCode());
        }
    }

    private boolean isCashMethod(PaymentMethod method) {
        return method != null && method.getAccount() != null
                && method.getAccount().getId().equals(accounts.cash().getId());
    }

    private String currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "system";
    }

    private void restoreAverageCost(Product product, Purchase purchase, PurchaseDetail detail,
                                    int currentQty, int reversedQty) {
        int remainingQty = currentQty - reversedQty;
        BigDecimal currentCost = safe(product.getCostPrice());
        if (remainingQty <= 0) {
            product.setCostPrice(BigDecimal.ZERO);
            return;
        }
        BigDecimal subtotal = safe(purchase.getTotalAmount());
        BigDecimal costTotal = subtotal.subtract(safe(purchase.getDiscountAmount()))
                .add(safe(purchase.getOtherCharges()));
        BigDecimal effectiveCost = safe(detail.getUnitCost());
        if (subtotal.signum() > 0)
            effectiveCost = effectiveCost.multiply(costTotal)
                    .divide(subtotal, 4, java.math.RoundingMode.HALF_UP);
        BigDecimal remainingValue = currentCost.multiply(BigDecimal.valueOf(currentQty))
                .subtract(effectiveCost.multiply(BigDecimal.valueOf(reversedQty)));
        BigDecimal restored = remainingValue.signum() < 0 ? BigDecimal.ZERO
                : remainingValue.divide(BigDecimal.valueOf(remainingQty), 2, java.math.RoundingMode.HALF_UP);
        product.setCostPrice(restored);
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

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getTrend(String dateFrom, String dateTo) {
        LocalDateTime from = parseDate(dateFrom, false);
        LocalDateTime to = parseDate(dateTo, true);
        return purchaseRepository.findDailyTrendByDateRange(from, to).stream().map(row -> {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            int day = ((Number) row[2]).intValue();
            java.util.Map<String, Object> point = new java.util.LinkedHashMap<>();
            point.put("date", LocalDate.of(year, month, day).toString());
            point.put("purchaseAmount", row[3]);
            point.put("paidAmount", row[4]);
            point.put("payableAmount", row[5]);
            point.put("count", row[6]);
            return point;
        }).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getTopSuppliers(String dateFrom, String dateTo) {
        LocalDateTime from = parseDate(dateFrom, false);
        LocalDateTime to = parseDate(dateTo, true);
        return purchaseRepository.findTopSuppliersByAmount(from, to).stream().map(row -> {
            java.util.Map<String, Object> point = new java.util.LinkedHashMap<>();
            point.put("supplierName", row[0]);
            point.put("supplierCode", row[1]);
            point.put("totalAmount", row[2]);
            point.put("count", row[3]);
            return point;
        }).toList();
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

    /**
     * ✅ Reorder suggestions — active products at/below their reorder level,
     * with suggested qty = top-up back to the reorder level.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public List<org.sspd.servicemgmt.purchaseoptions.dto.ReorderSuggestionDTO> getReorderSuggestions() {
        return productRepository.findReorderNeeded().stream()
                .map(p -> org.sspd.servicemgmt.purchaseoptions.dto.ReorderSuggestionDTO.builder()
                        .productId(p.getId())
                        .productName(p.getName())
                        .productCode(p.getProductCode())
                        .hasSerial(p.getHasSerial())
                        .stockQty(p.getStockQty() != null ? p.getStockQty() : 0)
                        .reorderLevel(p.getReorderLevel() != null ? p.getReorderLevel() : 0)
                        .suggestedQty(Math.max(1, (p.getReorderLevel() != null ? p.getReorderLevel() : 0) - (p.getStockQty() != null ? p.getStockQty() : 0)))
                        .lastCost(p.getCostPrice())
                        .build())
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public PurchaseDTO findById(Integer id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        return enrichWarrantyItems(mapper.toDto(purchase), purchase);
    }

    /**
     * ✅ Overdue payables — CONFIRMED vouchers with dueAmount > 0 whose dueDate has passed.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public List<PurchaseDTO> getOverdue() {
        return purchaseRepository.findOverduePayables(LocalDate.now()).stream()
                .map(entity -> enrichWarrantyItems(mapper.toDto(entity), entity))
                .toList();
    }

    private static final String[] EXCEL_HEADERS = {
        "ဘောင်ချာနံပါတ်", "ရက်စွဲ", "ပေးသွင်းသူ", "ဝန်ထမ်း", "ကုန်ဖိုး",
        "သက်သာစွာ", "အခွန်", "အခြားကုန်ကျစရိတ်", "ကျသင့်ငွေ",
        "ပေးချေပြီး", "ပေးရန်ကျန်", "ငွေပေးချေမှု", "အခြေအနေ"
    };

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public byte[] exportExcel(String dateFrom, String dateTo) throws java.io.IOException {
        LocalDateTime from = parseDate(dateFrom, false);
        LocalDateTime to = parseDate(dateTo, true);
        List<PurchaseDTO> purchases = purchaseRepository
                .findBySearchAndDateRange(null, from, to, org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE, Sort.by("id").descending()))
                .getContent().stream()
                .map(mapper::toDto)
                .toList();

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {

            var sheet = wb.createSheet("ဝယ်ယူမှုများ");

            var headerStyle = wb.createCellStyle();
            var headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());

            var headerRow = sheet.createRow(0);
            for (int i = 0; i < EXCEL_HEADERS.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(EXCEL_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            int rowNum = 1;
            for (PurchaseDTO p : purchases) {
                var row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getPurchaseCode() != null ? p.getPurchaseCode() : "#" + p.getId());
                row.createCell(1).setCellValue(p.getPurchaseDate() != null ? p.getPurchaseDate().toLocalDate().toString() : "");
                row.createCell(2).setCellValue(p.getSupplierName() != null ? p.getSupplierName() : "");
                row.createCell(3).setCellValue(p.getStaffName() != null ? p.getStaffName() : "");
                row.createCell(4).setCellValue(safe(p.getTotalAmount()).doubleValue());
                row.createCell(5).setCellValue(safe(p.getDiscountAmount()).doubleValue());
                row.createCell(6).setCellValue(safe(p.getTaxAmount()).doubleValue());
                row.createCell(7).setCellValue(safe(p.getOtherCharges()).doubleValue());
                row.createCell(8).setCellValue(safe(p.getNetAmount()).doubleValue());
                row.createCell(9).setCellValue(safe(p.getPaidAmount()).doubleValue());
                row.createCell(10).setCellValue(safe(p.getDueAmount()).doubleValue());
                row.createCell(11).setCellValue(p.getPaymentStatus() != null ? p.getPaymentStatus() : "");
                row.createCell(12).setCellValue(p.getStatus() != null ? p.getStatus() : "CONFIRMED");
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_UPDATE')")
    @Transactional
    public PurchaseDTO update(Integer id, PurchaseDTO dto) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        periodGuard.assertOpen(purchase.getPurchaseDate(), "update purchase");

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

    /**
     * ✅ Replace / remove supplier invoice attachment — metadata only, no stock/accounting impact.
     * Pass null for both arguments to clear the attachment.
     */
    public PurchaseDTO updateAttachment(Integer id, String attachmentName, String attachmentData) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        if (attachmentData != null && attachmentData.length() > 4_500_000) {
            throw new RuntimeException("Attachment too large (max ~3MB)");
        }
        purchase.setAttachmentName(attachmentName);
        purchase.setAttachmentData(attachmentData);
        Purchase saved = purchaseRepository.save(purchase);
        return enrichWarrantyItems(mapper.toDto(saved), saved);
    }

    /**
     * ✅ Draft → Confirm — applies the deferred side effects:
     * stock in, serial creation, warranty items, average cost, payment transactions and journal.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_UPDATE')")
    @Transactional
    public PurchaseDTO confirmDraft(Integer id, PurchaseDTO overrides) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        periodGuard.assertOpen(purchase.getPurchaseDate(), "confirm purchase");
        if (!purchase.isDraft())
            throw new RuntimeException("Only draft purchases can be confirmed.");
        if (purchase.getDetails() == null || purchase.getDetails().isEmpty())
            throw new RuntimeException("Draft purchase has no detail lines.");

        Supplier supplier = purchase.getSupplier();
        Staff staff = purchase.getStaff();

        // Duplicate submission guard
        BigDecimal estimatedTotal = purchase.getTotalAmount() != null ? purchase.getTotalAmount() : BigDecimal.ZERO;
        long recentCount = purchaseRepository.countRecentDuplicates(
                supplier.getId(), staff.getId(), estimatedTotal,
                LocalDateTime.now().minusSeconds(15));
        if (recentCount > 0)
            throw new RuntimeException("Duplicate purchase detected. ထပ်မနှိပ်ပါနှင့် — ခဏ စောင့်ပါ။");

        // Merge header overrides (dates / remark / discount / tax / charges / attachment)
        PurchaseDTO dto = mapper.toDto(purchase);
        if (overrides != null) {
            if (overrides.getPurchaseDate() != null) purchase.setPurchaseDate(overrides.getPurchaseDate());
            if (overrides.getDueDate() != null) purchase.setDueDate(overrides.getDueDate());
            if (overrides.getRemark() != null) purchase.setRemark(overrides.getRemark());
            if (overrides.getDiscountAmount() != null) dto.setDiscountAmount(overrides.getDiscountAmount());
            if (overrides.getTaxAmount() != null) dto.setTaxAmount(overrides.getTaxAmount());
            if (overrides.getOtherCharges() != null) dto.setOtherCharges(overrides.getOtherCharges());
            applyAttachment(purchase, overrides);
            if (overrides.getSupplierInvoiceNo() != null) {
                purchase.setSupplierInvoiceNo(overrides.getSupplierInvoiceNo().trim());
            }
            dto.setPayments(overrides.getPayments());
            dto.setPaymentMethodId(overrides.getPaymentMethodId());
            dto.setTransactionNo(overrides.getTransactionNo());
            dto.setPaidAmount(overrides.getPaidAmount());
        }
        validateSupplierInvoiceNumber(purchase.getSupplier().getId(), purchase.getSupplierInvoiceNo(), purchase.getId());
        validateStaffSelection(purchase.getStaff());
        validateTaxAndCharges(dto);
        java.util.List<String> budgetWarnings = purchaseBudgetService.validate((purchase.getPurchaseDate()!=null?purchase.getPurchaseDate():LocalDateTime.now()).toLocalDate(),
                overrides!=null&&overrides.getDetails()!=null&&!overrides.getDetails().isEmpty()?overrides.getDetails():dto.getDetails());
        LocalDate warrantyStart = (purchase.getPurchaseDate() != null ? purchase.getPurchaseDate() : LocalDateTime.now()).toLocalDate();

        Map<Integer, PurchaseDetailDTO> overrideByProduct = new HashMap<>();
        if (overrides != null && overrides.getDetails() != null) {
            for (PurchaseDetailDTO od : overrides.getDetails()) {
                if (od.getProductId() != null) overrideByProduct.put(od.getProductId(), od);
            }
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        Set<String> payloadSerials = new HashSet<>();

        for (PurchaseDetail detail : purchase.getDetails()) {
            Product product = detail.getProduct();
            int qty = detail.getQty() != null ? detail.getQty() : 0;
            BigDecimal subtotal = safe(detail.getUnitCost()).multiply(BigDecimal.valueOf(qty));
            calculatedTotal = calculatedTotal.add(subtotal);

            PurchaseDetailDTO od = overrideByProduct.get(product.getId());

            List<Integer> itemWarranties;
            List<String> serials = List.of();
            List<String> conditions = List.of();
            List<String> photos = List.of();
            if (od != null) {
                itemWarranties = normalizeItemWarranties(od);
                if (od.getSerialNumbers() != null && !od.getSerialNumbers().isEmpty()) serials = od.getSerialNumbers();
                if (od.getSerialConditions() != null) conditions = od.getSerialConditions();
                if (od.getSerialPhotos() != null) photos = od.getSerialPhotos();
            } else {
                int bulkMonths = detail.getWarrantyMonths() != null ? detail.getWarrantyMonths() : 0;
                itemWarranties = java.util.stream.IntStream.range(0, qty).mapToObj(i -> bulkMonths).toList();
                serials = detail.getWarrantyItems() == null ? List.of() : detail.getWarrantyItems().stream()
                        .sorted(Comparator.comparing(PurchaseDetailWarranty::getItemIndex))
                        .map(PurchaseDetailWarranty::getSerialNumber)
                        .filter(sn -> sn != null && !sn.isBlank())
                        .toList();
            }

            boolean hasSerials = Boolean.TRUE.equals(product.getHasSerial());
            if (hasSerials && serials.isEmpty())
                throw new RuntimeException("Serial numbers required for: " + product.getName());
            if (hasSerials && serials.size() != qty)
                throw new RuntimeException("Serial count must match Qty for: " + product.getName());
            if (!hasSerials && !serials.isEmpty())
                throw new RuntimeException("Product is non-serial. Remove serials for: " + product.getName());

            BigDecimal allocatedLanded = allocatedOtherCharge(detail.getUnitCost(), qty,
                    detail.getAllocatedLandedCost(), dto);
            detail.setAllocatedLandedCost(allocatedLanded);
            if (hasSerials) {
                updateAverageCost(product, effectiveUnitCost(detail.getUnitCost(), qty, allocatedLanded, dto), qty,
                        serialRepository.countByProductIdAndStatus(product.getId(), SerialStatus.Available).intValue());
                if (detail.getUnitCost() != null && detail.getUnitCost().compareTo(BigDecimal.ZERO) > 0)
                    product.setLastPurchaseCost(detail.getUnitCost());
                for (int i = 0; i < serials.size(); i++) {
                    String sn = serials.get(i);
                    if (sn == null || sn.isBlank())
                        throw new RuntimeException("Blank serial number found for: " + product.getName());
                    sn = sn.trim();
                    if (!payloadSerials.add(sn))
                        throw new RuntimeException("Duplicate serial in request: '" + sn + "'");
                    if (serialRepository.existsBySerialNumber(sn))
                        throw new RuntimeException("Serial '" + sn + "' already exists!");
                    Integer months = itemWarranties.size() > i ? itemWarranties.get(i) : 0;
                    String condition = conditions.size() > i ? conditions.get(i) : null;
                    String photo = photos.size() > i ? photos.get(i) : null;
                    serialRepository.save(ProductSerial.builder()
                            .product(product)
                            .serialNumber(sn)
                            .status(SerialStatus.Available)
                            .warrantyMonths(months)
                            .warrantyStartDate(warrantyStart)
                            .warrantyEndDate(warrantyStart.plusMonths(months != null ? months : 0))
                            .condition(condition)
                            .photoBase64(photo)
                            .build());
                    detail.getWarrantyItems().add(PurchaseDetailWarranty.builder()
                            .purchaseDetail(detail)
                            .itemIndex(i + 1)
                            .serialNumber(sn)
                            .warrantyMonths(months != null ? months : 0)
                            .warrantyStartDate(warrantyStart)
                            .warrantyEndDate(warrantyStart.plusMonths(months != null ? months : 0))
                            .build());
                }
            } else {
                int current = product.getStockQty() != null ? product.getStockQty() : 0;
                updateAverageCost(product, effectiveUnitCost(detail.getUnitCost(), qty, allocatedLanded, dto), qty, current);
                if (detail.getUnitCost() != null && detail.getUnitCost().compareTo(BigDecimal.ZERO) > 0)
                    product.setLastPurchaseCost(detail.getUnitCost());
                product.setStockQty(current + qty);
                productRepository.save(product);
                for (int i = 0; i < qty; i++) {
                    Integer months = itemWarranties.size() > i ? itemWarranties.get(i) : 0;
                    detail.getWarrantyItems().add(PurchaseDetailWarranty.builder()
                            .purchaseDetail(detail)
                            .itemIndex(i + 1)
                            .serialNumber(null)
                            .warrantyMonths(months != null ? months : 0)
                            .warrantyStartDate(warrantyStart)
                            .warrantyEndDate(warrantyStart.plusMonths(months != null ? months : 0))
                            .build());
                }
            }

            stockMovementService.recordMovement(StockMovement.builder()
                    .product(product).movementType(MovementType.IN).qty(qty)
                    .referenceType("Purchase").warehouseName(dto.getWarehouseName()).build());
        }

        purchase.setStatus(PurchaseStatus.CONFIRMED);
        purchase.setTotalAmount(calculatedTotal);
        BigDecimal discountAmount = safe(dto.getDiscountAmount());
        if (discountAmount.compareTo(BigDecimal.ZERO) < 0 || discountAmount.compareTo(calculatedTotal) > 0)
            throw new RuntimeException("Invalid discount amount.");
        BigDecimal netAmount = calculatePayableNet(calculatedTotal, discountAmount, dto);
        purchase.setDiscountAmount(discountAmount);
        purchase.setTaxAmount(safe(dto.getTaxAmount()));
        purchase.setOtherCharges(safe(dto.getOtherCharges()));
        purchase.setTaxMode(normalizeTaxMode(dto.getTaxMode()));
        purchase.setTaxRate(safe(dto.getTaxRate()));
        purchase.setWithholdingTaxAmount(safe(dto.getWithholdingTaxAmount()));
        purchase.setLandedCostAllocationMethod(normalizeAllocationMethod(dto.getLandedCostAllocationMethod()));
        purchase.setWarehouseName(dto.getWarehouseName());
        applyCurrencySnapshot(purchase, dto, netAmount);
        purchase.setNetAmount(netAmount);
        purchase.setPaidAmount(paymentTotal(dto.getPayments(), dto.getPaidAmount()));
        if (purchase.getPaidAmount().compareTo(netAmount) > 0)
            throw new RuntimeException("Paid amount cannot exceed net purchase amount.");
        purchase.setDueAmount(netAmount.subtract(purchase.getPaidAmount()));
        applyAndValidateSupplierCreditOverride(purchase, supplier, purchase.getDueAmount(), dto);
        if (purchase.getDueAmount().compareTo(BigDecimal.ZERO) <= 0)
            purchase.setPaymentStatus(PaymentStatus.Paid);
        else if (purchase.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)
            purchase.setPaymentStatus(PaymentStatus.Partial);
        else
            purchase.setPaymentStatus(PaymentStatus.Pending);
        purchase.setDueDate(resolveDueDate(dto, purchase));

        Purchase savedPurchase = purchaseRepository.save(purchase);
        stockLotService.receivePurchase(savedPurchase);
        syncSupplierBalance(supplier);

        createPurchasePaymentTransactions(savedPurchase, dto);
        createPurchaseJournal(savedPurchase, dto);

        messagingTemplate.convertAndSend(PURCHASE_TOPIC, "PURCHASE_CONFIRMED");
        return withBudgetWarnings(enrichWarrantyItems(mapper.toDto(savedPurchase), savedPurchase), budgetWarnings);
    }

    /**
     * ✅ Cancel / Void a purchase.
     * - DRAFT      → hard delete (no side effects were applied).
     * - CONFIRMED  → soft cancel with full reversal: stock out, serial removal,
     *                reversing journal ("VOID") and supplier balance re-sync.
     */
    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_DELETE')")
    @Transactional
    public PurchaseDTO cancel(Integer id, String reason) {
        return cancel(id, reason, null);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_DELETE')")
    @Transactional
    public PurchaseDTO cancel(Integer id, String reason, Integer refundPaymentMethodId) {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Cancellation reason is required.");
        String cleanReason = reason.trim();
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found: " + id));
        periodGuard.assertOpen(purchase.getPurchaseDate(), "cancel purchase");
        periodGuard.assertOpen(LocalDateTime.now(), "post purchase cancellation reversal");
        if (purchase.isCancelled())
            throw new RuntimeException("Purchase is already cancelled.");

        if (purchase.isDraft()) {
            purchase.setStatus(PurchaseStatus.CANCELLED);
            purchase.setPaymentStatus(PaymentStatus.Cancelled);
            purchase.setDueAmount(BigDecimal.ZERO);
            purchase.setCancelReason(cleanReason);
            purchase.setCancelledBy(currentActor());
            purchase.setCancelledAt(LocalDateTime.now());
            Purchase savedDraft = purchaseRepository.save(purchase);
            messagingTemplate.convertAndSend(PURCHASE_TOPIC, "PURCHASE_CANCELLED");
            return enrichWarrantyItems(mapper.toDto(savedDraft), savedDraft);
        }

        if (!purchaseReturnRepository.findByPurchaseId(id).isEmpty())
            throw new RuntimeException("Cannot cancel. This voucher already has purchase returns. Use returns instead.");
        stockLotService.cancelPurchase(purchase);

        for (PurchaseDetail detail : purchase.getDetails()) {
            Product product = detail.getProduct();
            int qty = detail.getQty() != null ? detail.getQty() : 0;

            List<String> serials = detail.getWarrantyItems() == null ? List.of() : detail.getWarrantyItems().stream()
                    .sorted(Comparator.comparing(PurchaseDetailWarranty::getItemIndex))
                    .map(PurchaseDetailWarranty::getSerialNumber)
                    .filter(sn -> sn != null && !sn.isBlank())
                    .toList();

            if (!serials.isEmpty()) {
                int availableBefore = serialRepository.countByProductIdAndStatus(product.getId(), SerialStatus.Available).intValue();
                restoreAverageCost(product, purchase, detail, availableBefore, qty);
                for (String sn : serials) {
                    ProductSerial serial = serialRepository.findBySerialNumber(sn)
                            .orElseThrow(() -> new RuntimeException("Serial '" + sn + "' not found while cancelling."));
                    if (serial.getStatus() != SerialStatus.Available)
                        throw new RuntimeException("Cannot cancel. Serial '" + sn + "' is no longer available (" + serial.getStatus() + ").");
                    serialRepository.delete(serial);
                }
                productRepository.save(product);
            } else if (!Boolean.TRUE.equals(product.getHasSerial())) {
                int current = product.getStockQty() != null ? product.getStockQty() : 0;
                if (current < qty)
                    throw new RuntimeException("Cannot cancel. Insufficient stock for '" + product.getName()
                            + "' (current: " + current + ", needed to reverse: " + qty + ").");
                restoreAverageCost(product, purchase, detail, current, qty);
                product.setStockQty(current - qty);
                productRepository.save(product);
            } else {
                // Serial product without stored serial rows — nothing to reverse on serials.
            }

            stockMovementService.recordMovement(StockMovement.builder()
                    .product(product).movementType(MovementType.OUT).qty(qty)
                    .referenceType("Purchase-Cancel").build());
        }

        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(purchase.getId(), ReferenceType.Purchase);
        BigDecimal paidTotal = BigDecimal.ZERO;
        for (PaymentTransaction tx : transactions) {
            if (Boolean.TRUE.equals(tx.getReversed())) continue;
            if (tx.getAmount() != null && tx.getAmount().compareTo(BigDecimal.ZERO) > 0)
                paidTotal = paidTotal.add(tx.getAmount());
        }
        if (paidTotal.compareTo(BigDecimal.ZERO) <= 0 && purchase.getPaidAmount() != null)
            paidTotal = purchase.getPaidAmount();

        PaymentMethod refundMethod = null;
        if (paidTotal.compareTo(BigDecimal.ZERO) > 0) {
            if (refundPaymentMethodId == null)
                throw new RuntimeException("ငွေပြန်ဝင်မည့် နည်းလမ်း ရွေးပါ။");
            refundMethod = paymentMethodRepository.findById(refundPaymentMethodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
            if (!refundMethod.isActive())
                throw new RuntimeException("Selected payment method is inactive.");
            if (refundMethod.getAccount() == null)
                throw new RuntimeException("Payment method has no linked account.");
        }

        createCancelJournal(purchase, paidTotal, refundMethod);

        LocalDateTime reversedAt = LocalDateTime.now();
        String actor = currentActor();
        for (PaymentTransaction tx : transactions) {
            if (Boolean.TRUE.equals(tx.getReversed())) continue;
            tx.setReversed(true);
            tx.setReversedAt(reversedAt);
            tx.setReversedBy(actor);
            tx.setReversalReason(cleanReason);
        }
        paymentTransactionRepository.saveAll(transactions);
        if (refundMethod != null && isCashMethod(refundMethod))
            cashDrawerService.recordPurchaseCashIn(paidTotal, "Purchase cancellation " + purchase.getPurchaseCode() + ": " + cleanReason);

        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchase.setDueAmount(BigDecimal.ZERO);
        purchase.setSupplierCreditAmount(BigDecimal.ZERO);
        purchase.setPaymentStatus(PaymentStatus.Cancelled);
        purchase.setCancelReason(cleanReason);
        purchase.setCancelledBy(actor);
        purchase.setCancelledAt(reversedAt);
        Purchase savedPurchase = purchaseRepository.save(purchase);
        syncSupplierBalance(purchase.getSupplier());

        messagingTemplate.convertAndSend(PURCHASE_TOPIC, "PURCHASE_CANCELLED");
        return enrichWarrantyItems(mapper.toDto(savedPurchase), savedPurchase);
    }

    /**
     * ✅ Cancel Journal — reverse purchases/VAT/payable, and bring paid money
     * back through the chosen refund method (may differ from the original cash/bank).
     */
    private void createCancelJournal(Purchase p, BigDecimal paidTotal, PaymentMethod refundMethod) {
        BigDecimal net = p.getNetAmount() != null ? p.getNetAmount()
                : (p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO);
        if (net.compareTo(BigDecimal.ZERO) <= 0) return;

        JournalEntryDTO journalDTO = new JournalEntryDTO();
        journalDTO.setReferenceNo((p.getPurchaseCode() != null ? p.getPurchaseCode() : String.valueOf(p.getId())) + "-VOID");
        journalDTO.setEntryDate(LocalDateTime.now());
        journalDTO.setDescription("Cancel Purchase from: " + p.getSupplier().getName());
        journalDTO.setStaffId(p.getStaff().getId());

        List<JournalDetailDTO> details = new ArrayList<>();

        BigDecimal tax = safe(p.getTaxAmount()).min(net);
        BigDecimal purchaseCost = net.subtract(tax);
        JournalDetailDTO crPurchases = new JournalDetailDTO();
        crPurchases.setAccountId(accounts.purchases().getId());  // EXP-007
        crPurchases.setDebit(BigDecimal.ZERO);
        crPurchases.setCredit(purchaseCost);
        if (purchaseCost.signum() > 0) details.add(crPurchases);
        if (tax.signum() > 0) {
            JournalDetailDTO crInputVat = new JournalDetailDTO();
            crInputVat.setAccountId(accounts.inputTaxReceivable().getId());
            crInputVat.setDebit(BigDecimal.ZERO);
            crInputVat.setCredit(tax);
            details.add(crInputVat);
        }

        BigDecimal originalDue = p.getDueAmount() != null ? p.getDueAmount() : BigDecimal.ZERO;
        if (originalDue.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO drPayable = new JournalDetailDTO();
            drPayable.setAccountId(accounts.payable().getId());  // LIA-002
            drPayable.setDebit(originalDue);
            drPayable.setCredit(BigDecimal.ZERO);
            details.add(drPayable);
        }

        BigDecimal paidReversed = paidTotal != null ? paidTotal : BigDecimal.ZERO;
        if (refundMethod != null && refundMethod.getAccount() != null && paidReversed.compareTo(BigDecimal.ZERO) > 0) {
            JournalDetailDTO drRefund = new JournalDetailDTO();
            drRefund.setAccountId(refundMethod.getAccount().getId());
            drRefund.setDebit(paidReversed);
            drRefund.setCredit(BigDecimal.ZERO);
            details.add(drRefund);
        }

        journalDTO.setDetails(details);
        journalWriter.write(journalDTO);
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
        if (purchase.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            purchase.setPaymentTermDays(0);
            return null;
        }
        LocalDate baseDate = purchase.getPurchaseDate() != null
                ? purchase.getPurchaseDate().toLocalDate()
                : LocalDate.now();
        if (dto.getDueDate() != null) {
            if (dto.getDueDate().isBefore(baseDate)) {
                throw new RuntimeException("Due date cannot be before purchase date.");
            }
            int days = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(baseDate, dto.getDueDate()));
            purchase.setPaymentTermDays(days);
            return dto.getDueDate();
        }
        int supplierDays = purchase.getSupplier() != null && purchase.getSupplier().getDefaultCreditDays() != null
                ? purchase.getSupplier().getDefaultCreditDays() : 30;
        int days = dto.getPaymentTermDays() != null ? dto.getPaymentTermDays() : supplierDays;
        if (days < 0) throw new RuntimeException("Payment term days cannot be negative.");
        purchase.setPaymentTermDays(days);
        return baseDate.plusDays(days);
    }

    private void applyAndValidateSupplierCreditOverride(Purchase purchase, Supplier supplier,
                                                         BigDecimal newDue, PurchaseDTO dto) {
        BigDecimal limit = supplier.getCreditLimit() != null ? supplier.getCreditLimit() : BigDecimal.ZERO;
        if (limit.compareTo(BigDecimal.ZERO) <= 0 || newDue.compareTo(BigDecimal.ZERO) <= 0) {
            purchase.setCreditLimitOverride(false);
            return;
        }
        BigDecimal current = supplier.getCurrentBalance() != null ? supplier.getCurrentBalance() : BigDecimal.ZERO;
        if (current.add(newDue).compareTo(limit) > 0) {
            boolean requested = Boolean.TRUE.equals(dto.getCreditLimitOverride());
            if (!requested || !hasAuthority("CAN_ACCESS_CREDIT_OVERRIDE_APPROVE"))
                throw new RuntimeException("Supplier credit limit exceeded. Manager override is required. Limit: " + limit
                        + ", current balance: " + current + ", new due: " + newDue);
            if (dto.getCreditOverrideReason() == null || dto.getCreditOverrideReason().isBlank())
                throw new RuntimeException("Credit limit override reason is required.");
            purchase.setCreditLimitOverride(true);
            purchase.setCreditOverrideReason(dto.getCreditOverrideReason().trim());
            purchase.setCreditOverrideBy(currentActor());
            purchase.setCreditOverrideAt(LocalDateTime.now());
        } else {
            purchase.setCreditLimitOverride(false);
            purchase.setCreditOverrideReason(null);
            purchase.setCreditOverrideBy(null);
            purchase.setCreditOverrideAt(null);
        }
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
        if (purchase.getPoId() != null) {
            dto.setPoId(purchase.getPoId());
            if (dto.getPoCode() == null || dto.getPoCode().isBlank()) {
                purchaseOrderRepository.findById(purchase.getPoId())
                        .ifPresent(po -> dto.setPoCode(po.getPoCode()));
            }
        }
        return dto;
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    public org.sspd.servicemgmt.purchaseoptions.budget.dto.PurchaseBudgetCheckDTO checkBudget(PurchaseDTO dto) {
        return purchaseBudgetService.evaluate(
                (dto.getPurchaseDate() != null ? dto.getPurchaseDate() : LocalDateTime.now()).toLocalDate(),
                dto.getDetails());
    }

    private PurchaseDTO withBudgetWarnings(PurchaseDTO dto, java.util.List<String> warnings) {
        dto.setBudgetWarnings(warnings == null ? java.util.List.of() : warnings);
        return dto;
    }
}
