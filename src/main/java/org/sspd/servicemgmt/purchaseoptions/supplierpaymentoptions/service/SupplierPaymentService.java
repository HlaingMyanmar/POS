package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.support.PaymentTransactionNumbers;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.PaymentStatus;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.dto.*;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.*;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierPaymentRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.supplieroptions.model.Supplier;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class SupplierPaymentService {
    private final SupplierPaymentRepository supplierPaymentRepository;
    private final org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository.SupplierCreditApplicationRepository creditApplicationRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseRepository purchaseRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final PaymentBalanceValidator paymentBalanceValidator;
    private final CashDrawerService cashDrawerService;
    private final JournalWriter journalWriter;
    private final AccountResolver accounts;
    private final org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard periodGuard;

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public SupplierPaymentDTO pay(SupplierPaymentRequest request) {
        periodGuard.assertOpen(LocalDateTime.now(), "record supplier payment");
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Payment amount must be greater than zero.");
        Supplier supplier = supplierRepository.findByIdForUpdate(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        PaymentMethod method = paymentMethodRepository.findById(request.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        if (method.getAccount() == null) throw new RuntimeException("Payment method must have a linked account.");
        Integer staffId = requireAuthenticatedStaffId();
        paymentBalanceValidator.validateSufficientBalance(method, request.getAmount());

        List<AllocationWork> work = resolveAllocations(request, supplier);
        BigDecimal allocated = work.stream().map(AllocationWork::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(request.getAmount()) > 0)
            throw new RuntimeException("Allocated amount exceeds total payment.");
        BigDecimal advance = request.getAmount().subtract(allocated);

        SupplierPayment payment = SupplierPayment.builder().paymentNo("PENDING").supplier(supplier)
                .paymentMethod(method).totalAmount(request.getAmount()).allocatedAmount(allocated)
                .advanceAmount(advance).paymentDate(LocalDateTime.now())
                .transactionNo(blank(request.getTransactionNo()) ? null : request.getTransactionNo().trim())
                .paidBy(currentUsername()).remark(request.getRemark()).build();
        payment = supplierPaymentRepository.save(payment);
        payment.setPaymentNo(String.format("SP-%06d", payment.getId()));
        if (blank(payment.getTransactionNo())) {
            payment.setTransactionNo(PaymentTransactionNumbers.documentNo("SPTX", payment.getId()));
        }
        payment = supplierPaymentRepository.save(payment);

        boolean onlyAllocated = work.size() == 1 && advance.signum() <= 0;
        boolean onlyAdvance = work.isEmpty() && advance.signum() > 0;
        List<SupplierPaymentAllocation> entities = new ArrayList<>();
        for (AllocationWork item : work) {
            Purchase purchase = item.purchase();
            purchase.setPaidAmount(safe(purchase.getPaidAmount()).add(item.amount()));
            purchase.setDueAmount(safe(purchase.getDueAmount()).subtract(item.amount()));
            purchase.setPaymentStatus(purchase.getDueAmount().compareTo(BigDecimal.ZERO) <= 0
                    ? PaymentStatus.Paid : PaymentStatus.Partial);
            purchaseRepository.save(purchase);
            entities.add(SupplierPaymentAllocation.builder().supplierPayment(payment)
                    .purchase(purchase).amount(item.amount()).build());
            recordPaymentTransaction(payment, method, ReferenceType.Purchase, purchase.getId(), item.amount(),
                    onlyAllocated ? payment.getTransactionNo() : null);
        }
        if (advance.signum() > 0) {
            recordPaymentTransaction(payment, method, ReferenceType.Supplier_Advance, supplier.getId(), advance,
                    onlyAdvance ? payment.getTransactionNo() : null);
            supplier.setAdvanceBalance(safe(supplier.getAdvanceBalance()).add(advance));
        }
        payment.setAllocations(entities);
        payment = supplierPaymentRepository.save(payment);

        syncSupplierBalance(supplier);
        postJournal(payment, staffId);
        if (isCash(method))
            cashDrawerService.recordPurchaseCashOut(request.getAmount(), "Supplier payment " + payment.getPaymentNo(),
                    "Supplier_Payment", payment.getId());
        return toDto(payment);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public SupplierPaymentDTO voidPayment(Integer id, String reason) {
        periodGuard.assertOpen(LocalDateTime.now(), "void supplier payment");
        SupplierPayment payment = supplierPaymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier payment not found"));
        if (Boolean.TRUE.equals(payment.getVoided()))
            throw new IllegalStateException("Payment is already voided.");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Void reason is required.");

        Supplier supplier = supplierRepository.findByIdForUpdate(payment.getSupplier().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        BigDecimal advanceToReverse = safe(payment.getAdvanceAmount());
        if (advanceToReverse.compareTo(safe(supplier.getAdvanceBalance())) > 0)
            throw new IllegalStateException("Payment advance has already been used and cannot be voided.");

        for (SupplierPaymentAllocation alloc : payment.getAllocations()) {
            Purchase purchase = purchaseRepository.findByIdForUpdate(alloc.getPurchase().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Allocated purchase not found"));
            if (purchase.isCancelled())
                throw new IllegalStateException(
                        "Cannot void payment: purchase " + purchase.getPurchaseCode() + " is cancelled.");
            if (!purchase.isEffectivelyConfirmed())
                throw new IllegalStateException(
                        "Cannot void payment: purchase " + purchase.getPurchaseCode() + " is not confirmed.");
            if (safe(purchase.getReturnAmount()).signum() > 0)
                throw new IllegalStateException(
                        "Cannot void payment: purchase " + purchase.getPurchaseCode()
                                + " has returns after payment. Void returns first or reverse manually.");
            if (safe(alloc.getAmount()).compareTo(safe(purchase.getPaidAmount())) > 0)
                throw new IllegalStateException(
                        "Cannot void payment: purchase " + purchase.getPurchaseCode()
                                + " no longer has enough paid amount to reverse.");
        }

        for (SupplierPaymentAllocation alloc : payment.getAllocations()) {
            Purchase purchase = purchaseRepository.findByIdForUpdate(alloc.getPurchase().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Allocated purchase not found"));
            BigDecimal amount = safe(alloc.getAmount());
            purchase.setPaidAmount(safe(purchase.getPaidAmount()).subtract(amount).max(BigDecimal.ZERO));
            purchase.setDueAmount(safe(purchase.getDueAmount()).add(amount));
            purchase.setPaymentStatus(safe(purchase.getPaidAmount()).signum() <= 0
                    ? PaymentStatus.Pending
                    : (safe(purchase.getDueAmount()).signum() <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial));
            purchaseRepository.save(purchase);
        }
        reverseLinkedTransactions(payment, reason.trim());
        if (advanceToReverse.signum() > 0) {
            supplier.setAdvanceBalance(safe(supplier.getAdvanceBalance()).subtract(advanceToReverse));
            supplierRepository.save(supplier);
        }
        journalWriter.reverseByReferenceNo(payment.getPaymentNo());
        if (isCash(payment.getPaymentMethod()))
            cashDrawerService.recordPurchaseCashIn(payment.getTotalAmount(),
                    "Void supplier payment " + payment.getPaymentNo(),
                    "Supplier_Payment", payment.getId());

        payment.setVoided(true);
        payment.setVoidedAt(LocalDateTime.now());
        payment.setVoidedBy(currentUsername());
        payment.setVoidReason(reason.trim());
        syncSupplierBalance(supplier);
        return toDto(supplierPaymentRepository.save(payment));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @Transactional(readOnly = true)
    public List<SupplierPaymentDTO> history(Integer supplierId) {
        return supplierPaymentRepository.findBySupplierIdOrderByIdDesc(supplierId).stream().map(this::toDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> payables(Integer supplierId) {
        supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        return purchaseRepository.findSupplierPayablesFifo(supplierId).stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("purchaseId", p.getId()); row.put("purchaseCode", p.getPurchaseCode());
            row.put("purchaseDate", p.getPurchaseDate()); row.put("dueDate", p.getDueDate());
            row.put("netAmount", p.getNetAmount()); row.put("paidAmount", p.getPaidAmount());
            row.put("dueAmount", p.getDueAmount()); return row;
        }).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public Map<String, Object> applyCredit(SupplierCreditApplyRequest request) {
        periodGuard.assertOpen(LocalDateTime.now(), "apply supplier credit");
        Supplier supplier = supplierRepository.findByIdForUpdate(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        Purchase target = purchaseRepository.findByIdForUpdate(request.getPurchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (target.getSupplier() == null || !supplier.getId().equals(target.getSupplier().getId()))
            throw new RuntimeException("Target voucher belongs to another supplier.");
        requireConfirmedPurchase(target);
        Integer staffId = requireAuthenticatedStaffId();
        BigDecimal amount = safe(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(safe(target.getDueAmount())) > 0)
            throw new RuntimeException("Credit amount must be within target voucher due amount.");

        BigDecimal availableAdvance = safe(supplier.getAdvanceBalance());
        List<Purchase> creditSources = purchaseRepository.findSupplierCreditSourcesFifoForUpdate(supplier.getId());
        BigDecimal availableReturnCredit = creditSources.stream().map(Purchase::getSupplierCreditAmount)
                .map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(availableAdvance.add(availableReturnCredit)) > 0)
            throw new RuntimeException("Insufficient supplier credit.");

        BigDecimal advanceUsed = amount.min(availableAdvance);
        BigDecimal returnUsed = amount.subtract(advanceUsed);
        supplier.setAdvanceBalance(availableAdvance.subtract(advanceUsed));
        BigDecimal remainingReturn = returnUsed;
        StringBuilder sourceUses = new StringBuilder();
        for (Purchase source : creditSources) {
            if (remainingReturn.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal used = remainingReturn.min(safe(source.getSupplierCreditAmount()));
            if (used.compareTo(BigDecimal.ZERO) <= 0) continue;
            source.setSupplierCreditAmount(safe(source.getSupplierCreditAmount()).subtract(used));
            purchaseRepository.save(source);
            remainingReturn = remainingReturn.subtract(used);
            if (sourceUses.length() > 0) sourceUses.append(',');
            sourceUses.append(source.getId()).append(':').append(used.toPlainString());
        }

        target.setPaidAmount(safe(target.getPaidAmount()).add(amount));
        target.setDueAmount(safe(target.getDueAmount()).subtract(amount));
        target.setPaymentStatus(target.getDueAmount().compareTo(BigDecimal.ZERO) <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial);
        purchaseRepository.save(target);

        var application = org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication.builder()
                .applicationNo("PENDING").supplier(supplier).targetPurchase(target).amount(amount)
                .advanceUsed(advanceUsed).returnCreditUsed(returnUsed)
                .returnCreditSources(sourceUses.length() > 0 ? sourceUses.toString() : null)
                .appliedAt(LocalDateTime.now())
                .appliedBy(currentUsername()).reason(request.getReason()).voided(false).build();
        application = creditApplicationRepository.save(application);
        application.setApplicationNo(String.format("SCA-%06d", application.getId()));
        application = creditApplicationRepository.save(application);
        syncSupplierBalance(supplier);

        if (advanceUsed.compareTo(BigDecimal.ZERO) > 0) {
            JournalEntryDTO entry = new JournalEntryDTO();
            entry.setReferenceNo(application.getApplicationNo()); entry.setEntryDate(application.getAppliedAt());
            entry.setDescription("Apply supplier advance to " + target.getPurchaseCode()); entry.setStaffId(staffId);
            entry.setDetails(List.of(line(accounts.payable().getId(), advanceUsed, BigDecimal.ZERO),
                    line(accounts.supplierAdvance().getId(), BigDecimal.ZERO, advanceUsed)));
            journalWriter.write(entry);
        }
        if (returnUsed.compareTo(BigDecimal.ZERO) > 0) {
            JournalEntryDTO entry = new JournalEntryDTO();
            entry.setReferenceNo(application.getApplicationNo() + "-RC"); entry.setEntryDate(application.getAppliedAt());
            entry.setDescription("Apply supplier return credit to " + target.getPurchaseCode()); entry.setStaffId(staffId);
            // Clear AP on target against the supplier-credit asset created by the return journal.
            entry.setDetails(List.of(line(accounts.payable().getId(), returnUsed, BigDecimal.ZERO),
                    line(accounts.supplierAdvance().getId(), BigDecimal.ZERO, returnUsed)));
            journalWriter.write(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", application.getId());
        result.put("applicationNo", application.getApplicationNo()); result.put("amount", amount);
        result.put("advanceUsed", advanceUsed); result.put("returnCreditUsed", returnUsed);
        result.put("remainingDue", target.getDueAmount()); return result;
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_READ')")
    @Transactional(readOnly = true)
    public List<SupplierCreditApplicationDTO> creditApplications(Integer supplierId) {
        supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        return creditApplicationRepository.findBySupplierIdOrderByIdDesc(supplierId).stream()
                .map(this::toCreditDto).toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public SupplierCreditApplicationDTO voidCreditApplication(Integer id, String reason) {
        periodGuard.assertOpen(LocalDateTime.now(), "void supplier credit application");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Void reason is required.");

        var snapshot = creditApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier credit application not found"));
        if (snapshot.getSupplier() == null || snapshot.getSupplier().getId() == null)
            throw new ResourceNotFoundException("Supplier not found");
        Supplier supplier = supplierRepository.findByIdForUpdate(snapshot.getSupplier().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));

        java.util.TreeSet<Integer> purchaseIds = new java.util.TreeSet<>();
        if (snapshot.getTargetPurchase() != null && snapshot.getTargetPurchase().getId() != null)
            purchaseIds.add(snapshot.getTargetPurchase().getId());
        purchaseIds.addAll(parseReturnCreditSourceIds(snapshot.getReturnCreditSources()));
        for (Integer purchaseId : purchaseIds) {
            purchaseRepository.findByIdForUpdate(purchaseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        }

        var application = creditApplicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier credit application not found"));
        if (Boolean.TRUE.equals(application.getVoided()))
            throw new IllegalStateException("Credit application is already voided.");
        if (application.getSupplier() == null || !supplier.getId().equals(application.getSupplier().getId()))
            throw new IllegalStateException("Credit application supplier changed during void.");
        Purchase target = purchaseRepository.findByIdForUpdate(application.getTargetPurchase().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (target.isCancelled())
            throw new IllegalStateException(
                    "Cannot void credit application: purchase " + target.getPurchaseCode() + " is cancelled.");
        requireConfirmedPurchase(target);
        BigDecimal amount = safe(application.getAmount());
        if (amount.compareTo(safe(target.getPaidAmount())) > 0)
            throw new IllegalStateException(
                    "Cannot void credit application: purchase " + target.getPurchaseCode()
                            + " no longer has enough paid amount to reverse.");

        target.setPaidAmount(safe(target.getPaidAmount()).subtract(amount).max(BigDecimal.ZERO));
        target.setDueAmount(safe(target.getDueAmount()).add(amount));
        target.setPaymentStatus(safe(target.getPaidAmount()).signum() <= 0
                ? PaymentStatus.Pending
                : (safe(target.getDueAmount()).signum() <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial));
        purchaseRepository.save(target);

        supplier.setAdvanceBalance(safe(supplier.getAdvanceBalance()).add(safe(application.getAdvanceUsed())));
        BigDecimal remaining = restoreReturnCreditToRecordedSources(
                supplier.getId(), target.getId(), application.getReturnCreditSources(),
                safe(application.getReturnCreditUsed()));
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalStateException(
                    "Cannot restore supplier return credit to its original voucher for "
                            + application.getApplicationNo()
                            + ". Source history is missing or the source voucher is no longer available.");
        }

        journalWriter.reverseByReferenceNo(application.getApplicationNo());
        journalWriter.reverseByReferenceNo(application.getApplicationNo() + "-RC");

        application.setVoided(true);
        application.setVoidedAt(LocalDateTime.now());
        application.setVoidedBy(currentUsername());
        application.setVoidReason(reason.trim());
        application = creditApplicationRepository.save(application);
        syncSupplierBalance(supplier);
        return toCreditDto(application);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PURCHASE_READ')")
    @Transactional(readOnly = true)
    public Map<String, Object> creditSummary(Integer supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        BigDecimal returnCredit = purchaseRepository.findSupplierCreditSourcesFifo(supplierId).stream()
                .map(Purchase::getSupplierCreditAmount).map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("advanceBalance", safe(supplier.getAdvanceBalance()));
        result.put("returnCreditBalance", returnCredit);
        result.put("availableCredit", safe(supplier.getAdvanceBalance()).add(returnCredit));
        return result;
    }

    private List<AllocationWork> resolveAllocations(SupplierPaymentRequest request, Supplier supplier) {
        List<AllocationWork> result = new ArrayList<>();
        if (request.getAllocations() != null && !request.getAllocations().isEmpty()) {
            Set<Integer> seen = new HashSet<>();
            for (var requested : request.getAllocations()) {
                if (requested.getPurchaseId() == null || !seen.add(requested.getPurchaseId()))
                    throw new RuntimeException("Duplicate or missing purchase allocation.");
                Purchase purchase = purchaseRepository.findByIdForUpdate(requested.getPurchaseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
                if (purchase.getSupplier() == null || !supplier.getId().equals(purchase.getSupplier().getId()))
                    throw new RuntimeException("Allocated purchase belongs to another supplier.");
                requireConfirmedPurchase(purchase);
                BigDecimal amount = safe(requested.getAmount());
                if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(safe(purchase.getDueAmount())) > 0)
                    throw new RuntimeException("Invalid allocation for " + purchase.getPurchaseCode());
                result.add(new AllocationWork(purchase, amount));
            }
            return result;
        }
        BigDecimal remaining = request.getAmount();
        for (Purchase purchase : purchaseRepository.findSupplierPayablesFifoForUpdate(supplier.getId())) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal amount = remaining.min(safe(purchase.getDueAmount()));
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                result.add(new AllocationWork(purchase, amount));
                remaining = remaining.subtract(amount);
            }
        }
        return result;
    }

    private void postJournal(SupplierPayment payment, Integer staffId) {
        JournalEntryDTO entry = new JournalEntryDTO();
        entry.setReferenceNo(payment.getPaymentNo()); entry.setEntryDate(payment.getPaymentDate());
        entry.setDescription("Supplier payment: " + payment.getSupplier().getName()); entry.setStaffId(staffId);
        List<JournalDetailDTO> lines = new ArrayList<>();
        if (payment.getAllocatedAmount().compareTo(BigDecimal.ZERO) > 0)
            lines.add(line(accounts.payable().getId(), payment.getAllocatedAmount(), BigDecimal.ZERO));
        if (payment.getAdvanceAmount().compareTo(BigDecimal.ZERO) > 0)
            lines.add(line(accounts.supplierAdvance().getId(), payment.getAdvanceAmount(), BigDecimal.ZERO));
        lines.add(line(payment.getPaymentMethod().getAccount().getId(), BigDecimal.ZERO, payment.getTotalAmount()));
        entry.setDetails(lines); journalWriter.write(entry);
    }

    private PaymentTransaction recordPaymentTransaction(SupplierPayment payment, PaymentMethod method,
            ReferenceType type, Integer referenceId, BigDecimal amount, String transactionNo) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setReferenceId(referenceId);
        tx.setReferenceType(type);
        tx.setSourceType(PaymentTransaction.SOURCE_SUPPLIER_PAYMENT);
        tx.setSourceId(payment.getId());
        tx.setPaymentMethod(method);
        tx.setAmount(amount);
        tx.setPaymentDate(LocalDateTime.now());
        tx.setTransactionNo(transactionNo);
        return PaymentTransactionNumbers.save(paymentTransactionRepository, tx);
    }

    private JournalDetailDTO line(Integer accountId, BigDecimal debit, BigDecimal credit) {
        JournalDetailDTO line = new JournalDetailDTO(); line.setAccountId(accountId);
        line.setDebit(debit); line.setCredit(credit); return line;
    }
    private void requireConfirmedPurchase(Purchase purchase) {
        if (!purchase.isEffectivelyConfirmed())
            throw new RuntimeException("Cannot pay or apply credit to a draft or cancelled purchase.");
    }

    private void syncSupplierBalance(Supplier supplier) {
        BigDecimal due = purchaseRepository.sumDueAmountBySupplierId(supplier.getId());
        BigDecimal credits = purchaseRepository.sumSupplierCreditAmountBySupplierId(supplier.getId());
        supplier.setCurrentBalance(safe(supplier.getOpeningBalance()).add(safe(due))
                .subtract(safe(credits)).subtract(safe(supplier.getAdvanceBalance())));
        supplierRepository.save(supplier);
    }
    private SupplierCreditApplicationDTO toCreditDto(org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication application) {
        Purchase target = application.getTargetPurchase();
        return SupplierCreditApplicationDTO.builder()
                .id(application.getId())
                .applicationNo(application.getApplicationNo())
                .supplierId(application.getSupplier() != null ? application.getSupplier().getId() : null)
                .purchaseId(target != null ? target.getId() : null)
                .purchaseCode(target != null ? target.getPurchaseCode() : null)
                .amount(application.getAmount())
                .advanceUsed(application.getAdvanceUsed())
                .returnCreditUsed(application.getReturnCreditUsed())
                .appliedAt(application.getAppliedAt())
                .appliedBy(application.getAppliedBy())
                .reason(application.getReason())
                .voided(Boolean.TRUE.equals(application.getVoided()))
                .voidedAt(application.getVoidedAt())
                .voidedBy(application.getVoidedBy())
                .voidReason(application.getVoidReason())
                .build();
    }

    private java.util.List<Integer> parseReturnCreditSourceIds(String encodedSources) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (encodedSources == null || encodedSources.isBlank()) return ids;
        for (String token : encodedSources.split(",")) {
            String[] parts = token.split(":");
            if (parts.length != 2) continue;
            try {
                ids.add(Integer.valueOf(parts[0].trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed source history
            }
        }
        return ids;
    }

    private BigDecimal restoreReturnCreditToRecordedSources(Integer supplierId, Integer targetPurchaseId,
            String encodedSources, BigDecimal remaining) {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 || encodedSources == null || encodedSources.isBlank()) {
            return remaining;
        }
        for (String token : encodedSources.split(",")) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            String[] parts = token.split(":");
            if (parts.length != 2) continue;
            Integer sourceId;
            BigDecimal amount;
            try {
                sourceId = Integer.valueOf(parts[0].trim());
                amount = new BigDecimal(parts[1].trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0 || sourceId.equals(targetPurchaseId)) continue;
            Purchase source = purchaseRepository.findByIdForUpdate(sourceId).orElse(null);
            if (source == null || source.isCancelled() || source.getSupplier() == null
                    || !supplierId.equals(source.getSupplier().getId())) {
                continue;
            }
            BigDecimal restore = amount.min(remaining);
            source.setSupplierCreditAmount(safe(source.getSupplierCreditAmount()).add(restore));
            purchaseRepository.save(source);
            remaining = remaining.subtract(restore);
        }
        return remaining;
    }

    private SupplierPaymentDTO toDto(SupplierPayment p) {
        return SupplierPaymentDTO.builder().id(p.getId()).paymentNo(p.getPaymentNo())
                .supplierId(p.getSupplier().getId()).supplierName(p.getSupplier().getName())
                .paymentMethodId(p.getPaymentMethod().getId()).paymentMethodName(p.getPaymentMethod().getMethodName())
                .totalAmount(p.getTotalAmount()).allocatedAmount(p.getAllocatedAmount()).advanceAmount(p.getAdvanceAmount())
                .paymentDate(p.getPaymentDate()).transactionNo(p.getTransactionNo()).paidBy(p.getPaidBy()).remark(p.getRemark())
                .voided(Boolean.TRUE.equals(p.getVoided())).voidedAt(p.getVoidedAt()).voidedBy(p.getVoidedBy()).voidReason(p.getVoidReason())
                .allocations(p.getAllocations().stream().map(a -> SupplierPaymentDTO.Allocation.builder()
                        .purchaseId(a.getPurchase().getId()).purchaseCode(a.getPurchase().getPurchaseCode())
                        .amount(a.getAmount()).remainingDue(a.getPurchase().getDueAmount()).build()).toList()).build();
    }

    private void reverseLinkedTransactions(SupplierPayment payment, String reason) {
        List<PaymentTransaction> linked = new ArrayList<>(paymentTransactionRepository
                .findBySourceTypeAndSourceId(PaymentTransaction.SOURCE_SUPPLIER_PAYMENT, payment.getId()));
        linked.removeIf(tx -> Boolean.TRUE.equals(tx.getReversed()));
        if (linked.isEmpty() && !blank(payment.getTransactionNo())) {
            Set<Integer> seen = new HashSet<>();
            for (SupplierPaymentAllocation alloc : payment.getAllocations()) {
                if (alloc.getPurchase() == null || alloc.getPurchase().getId() == null) continue;
                paymentTransactionRepository.findByReferenceIdAndReferenceType(alloc.getPurchase().getId(), ReferenceType.Purchase)
                        .stream()
                        .filter(tx -> payment.getTransactionNo().equals(tx.getTransactionNo()))
                        .filter(tx -> !Boolean.TRUE.equals(tx.getReversed()))
                        .filter(tx -> seen.add(tx.getId()))
                        .forEach(linked::add);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String actor = currentUsername();
        for (PaymentTransaction tx : linked) {
            tx.setReversed(true);
            tx.setReversedAt(now);
            tx.setReversedBy(actor);
            tx.setReversalReason(reason);
            paymentTransactionRepository.save(tx);
        }
    }

    private boolean isCash(PaymentMethod method) {
        String name = method.getMethodName() == null ? "" : method.getMethodName().toLowerCase();
        return name.contains("cash") || name.contains("ငွေသား");
    }
    private Integer requireAuthenticatedStaffId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated staff is required for supplier payment journal.");
        }
        Integer staffId = userRepository.findByUsernameOrEmail(auth.getName(), auth.getName())
                .map(user -> user.getStaff() == null ? null : user.getStaff().getId())
                .orElse(null);
        if (staffId == null || !staffRepository.existsById(staffId)) {
            throw new IllegalArgumentException("Authenticated user is not linked to a staff record.");
        }
        return staffId;
    }
    private String currentUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private record AllocationWork(Purchase purchase, BigDecimal amount) {}
}
