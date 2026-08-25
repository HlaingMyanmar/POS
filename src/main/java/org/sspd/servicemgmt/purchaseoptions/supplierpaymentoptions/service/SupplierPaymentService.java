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
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        PaymentMethod method = paymentMethodRepository.findById(request.getPaymentMethodId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        if (method.getAccount() == null) throw new RuntimeException("Payment method must have a linked account.");
        if (request.getStaffId() == null || !staffRepository.existsById(request.getStaffId()))
            throw new RuntimeException("Valid staff is required for supplier payment journal.");
        paymentBalanceValidator.validateSufficientBalance(method, request.getAmount());

        List<AllocationWork> work = resolveAllocations(request, supplier);
        BigDecimal allocated = work.stream().map(AllocationWork::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(request.getAmount()) > 0)
            throw new RuntimeException("Allocated amount exceeds total payment.");
        BigDecimal advance = request.getAmount().subtract(allocated);

        SupplierPayment payment = SupplierPayment.builder().paymentNo("PENDING").supplier(supplier)
                .paymentMethod(method).totalAmount(request.getAmount()).allocatedAmount(allocated)
                .advanceAmount(advance).paymentDate(LocalDateTime.now())
                .transactionNo(blank(request.getTransactionNo()) ? nextTransactionNo() : request.getTransactionNo().trim())
                .paidBy(currentUsername()).remark(request.getRemark()).build();
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
            PaymentTransaction tx = new PaymentTransaction();
            tx.setReferenceId(purchase.getId()); tx.setReferenceType(ReferenceType.Purchase);
            tx.setPaymentMethod(method); tx.setAmount(item.amount()); tx.setPaymentDate(LocalDateTime.now());
            tx.setTransactionNo(payment.getTransactionNo());
            paymentTransactionRepository.save(tx);
        }
        payment.setAllocations(entities);
        payment = supplierPaymentRepository.save(payment);
        payment.setPaymentNo(String.format("SP-%06d", payment.getId()));
        payment = supplierPaymentRepository.save(payment);

        if (advance.compareTo(BigDecimal.ZERO) > 0)
            supplier.setAdvanceBalance(safe(supplier.getAdvanceBalance()).add(advance));
        syncSupplierBalance(supplier);
        postJournal(payment, request.getStaffId());
        if (isCash(method))
            cashDrawerService.recordPurchaseCashOut(request.getAmount(), "Supplier payment " + payment.getPaymentNo());
        return toDto(payment);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_PAYMENT_TRANSACTION_CREATE')")
    @Transactional
    public SupplierPaymentDTO voidPayment(Integer id, String reason, Integer staffId) {
        periodGuard.assertOpen(LocalDateTime.now(), "void supplier payment");
        SupplierPayment payment = supplierPaymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier payment not found"));
        if (Boolean.TRUE.equals(payment.getVoided()))
            throw new IllegalStateException("Payment is already voided.");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Void reason is required.");
        if (staffId == null || !staffRepository.existsById(staffId))
            throw new IllegalArgumentException("Valid staff is required.");

        for (SupplierPaymentAllocation alloc : payment.getAllocations()) {
            Purchase purchase = alloc.getPurchase();
            if (safe(purchase.getReturnAmount()).signum() > 0)
                throw new IllegalStateException(
                        "Cannot void payment: purchase " + purchase.getPurchaseCode()
                                + " has returns after payment. Void returns first or reverse manually.");
        }

        for (SupplierPaymentAllocation alloc : payment.getAllocations()) {
            Purchase purchase = alloc.getPurchase();
            BigDecimal amount = safe(alloc.getAmount());
            purchase.setPaidAmount(safe(purchase.getPaidAmount()).subtract(amount).max(BigDecimal.ZERO));
            purchase.setDueAmount(safe(purchase.getDueAmount()).add(amount));
            purchase.setPaymentStatus(safe(purchase.getPaidAmount()).signum() <= 0
                    ? PaymentStatus.Pending
                    : (safe(purchase.getDueAmount()).signum() <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial));
            purchaseRepository.save(purchase);
            paymentTransactionRepository.findByReferenceIdAndReferenceType(purchase.getId(), ReferenceType.Purchase).stream()
                    .filter(tx -> payment.getTransactionNo() != null && payment.getTransactionNo().equals(tx.getTransactionNo()))
                    .filter(tx -> !Boolean.TRUE.equals(tx.getReversed()))
                    .forEach(tx -> {
                        tx.setReversed(true);
                        tx.setReversedAt(LocalDateTime.now());
                        tx.setReversedBy(currentUsername());
                        tx.setReversalReason(reason.trim());
                        paymentTransactionRepository.save(tx);
                    });
        }
        if (safe(payment.getAdvanceAmount()).signum() > 0) {
            Supplier supplier = payment.getSupplier();
            supplier.setAdvanceBalance(safe(supplier.getAdvanceBalance()).subtract(safe(payment.getAdvanceAmount())).max(BigDecimal.ZERO));
            supplierRepository.save(supplier);
        }
        journalWriter.reverseByReferenceNo(payment.getPaymentNo());
        if (isCash(payment.getPaymentMethod()))
            cashDrawerService.recordPurchaseCashIn(payment.getTotalAmount(), "Void supplier payment " + payment.getPaymentNo());

        payment.setVoided(true);
        payment.setVoidedAt(LocalDateTime.now());
        payment.setVoidedBy(currentUsername());
        payment.setVoidReason(reason.trim());
        syncSupplierBalance(payment.getSupplier());
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
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found"));
        Purchase target = purchaseRepository.findById(request.getPurchaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
        if (target.getSupplier() == null || !supplier.getId().equals(target.getSupplier().getId()))
            throw new RuntimeException("Target voucher belongs to another supplier.");
        if (request.getStaffId() == null || !staffRepository.existsById(request.getStaffId()))
            throw new RuntimeException("Valid staff is required.");
        BigDecimal amount = safe(request.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(safe(target.getDueAmount())) > 0)
            throw new RuntimeException("Credit amount must be within target voucher due amount.");

        BigDecimal availableAdvance = safe(supplier.getAdvanceBalance());
        List<Purchase> creditSources = purchaseRepository.findSupplierCreditSourcesFifo(supplier.getId());
        BigDecimal availableReturnCredit = creditSources.stream().map(Purchase::getSupplierCreditAmount)
                .map(this::safe).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(availableAdvance.add(availableReturnCredit)) > 0)
            throw new RuntimeException("Insufficient supplier credit.");

        BigDecimal advanceUsed = amount.min(availableAdvance);
        BigDecimal returnUsed = amount.subtract(advanceUsed);
        supplier.setAdvanceBalance(availableAdvance.subtract(advanceUsed));
        BigDecimal remainingReturn = returnUsed;
        for (Purchase source : creditSources) {
            if (remainingReturn.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal used = remainingReturn.min(safe(source.getSupplierCreditAmount()));
            source.setSupplierCreditAmount(safe(source.getSupplierCreditAmount()).subtract(used));
            purchaseRepository.save(source);
            remainingReturn = remainingReturn.subtract(used);
        }

        target.setPaidAmount(safe(target.getPaidAmount()).add(amount));
        target.setDueAmount(safe(target.getDueAmount()).subtract(amount));
        target.setPaymentStatus(target.getDueAmount().compareTo(BigDecimal.ZERO) <= 0 ? PaymentStatus.Paid : PaymentStatus.Partial);
        purchaseRepository.save(target);

        var application = org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication.builder()
                .applicationNo("PENDING").supplier(supplier).targetPurchase(target).amount(amount)
                .advanceUsed(advanceUsed).returnCreditUsed(returnUsed).appliedAt(LocalDateTime.now())
                .appliedBy(currentUsername()).reason(request.getReason()).build();
        application = creditApplicationRepository.save(application);
        application.setApplicationNo(String.format("SCA-%06d", application.getId()));
        application = creditApplicationRepository.save(application);
        syncSupplierBalance(supplier);

        if (advanceUsed.compareTo(BigDecimal.ZERO) > 0) {
            JournalEntryDTO entry = new JournalEntryDTO();
            entry.setReferenceNo(application.getApplicationNo()); entry.setEntryDate(application.getAppliedAt());
            entry.setDescription("Apply supplier advance to " + target.getPurchaseCode()); entry.setStaffId(request.getStaffId());
            entry.setDetails(List.of(line(accounts.payable().getId(), advanceUsed, BigDecimal.ZERO),
                    line(accounts.supplierAdvance().getId(), BigDecimal.ZERO, advanceUsed)));
            journalWriter.write(entry);
        }
        if (returnUsed.compareTo(BigDecimal.ZERO) > 0) {
            JournalEntryDTO entry = new JournalEntryDTO();
            entry.setReferenceNo(application.getApplicationNo() + "-RC"); entry.setEntryDate(application.getAppliedAt());
            entry.setDescription("Apply supplier return credit to " + target.getPurchaseCode()); entry.setStaffId(request.getStaffId());
            // Clear AP on target against the supplier-credit asset created by the return journal.
            entry.setDetails(List.of(line(accounts.payable().getId(), returnUsed, BigDecimal.ZERO),
                    line(accounts.supplierAdvance().getId(), BigDecimal.ZERO, returnUsed)));
            journalWriter.write(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applicationNo", application.getApplicationNo()); result.put("amount", amount);
        result.put("advanceUsed", advanceUsed); result.put("returnCreditUsed", returnUsed);
        result.put("remainingDue", target.getDueAmount()); return result;
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
                Purchase purchase = purchaseRepository.findById(requested.getPurchaseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Purchase not found"));
                if (purchase.getSupplier() == null || !supplier.getId().equals(purchase.getSupplier().getId()))
                    throw new RuntimeException("Allocated purchase belongs to another supplier.");
                BigDecimal amount = safe(requested.getAmount());
                if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(safe(purchase.getDueAmount())) > 0)
                    throw new RuntimeException("Invalid allocation for " + purchase.getPurchaseCode());
                result.add(new AllocationWork(purchase, amount));
            }
            return result;
        }
        BigDecimal remaining = request.getAmount();
        for (Purchase purchase : purchaseRepository.findSupplierPayablesFifo(supplier.getId())) {
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

    private JournalDetailDTO line(Integer accountId, BigDecimal debit, BigDecimal credit) {
        JournalDetailDTO line = new JournalDetailDTO(); line.setAccountId(accountId);
        line.setDebit(debit); line.setCredit(credit); return line;
    }
    private void syncSupplierBalance(Supplier supplier) {
        BigDecimal due = purchaseRepository.sumDueAmountBySupplierId(supplier.getId());
        BigDecimal credits = purchaseRepository.sumSupplierCreditAmountBySupplierId(supplier.getId());
        supplier.setCurrentBalance(safe(supplier.getOpeningBalance()).add(safe(due))
                .subtract(safe(credits)).subtract(safe(supplier.getAdvanceBalance())));
        supplierRepository.save(supplier);
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
    private boolean isCash(PaymentMethod method) {
        String name = method.getMethodName() == null ? "" : method.getMethodName().toLowerCase();
        return name.contains("cash") || name.contains("ငွေသား");
    }
    private String currentUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }
    private String nextTransactionNo() { return "SPTX-" + System.currentTimeMillis(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private BigDecimal safe(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private record AllocationWork(Purchase purchase, BigDecimal amount) {}
}
