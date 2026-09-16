package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.beans.factory.ObjectProvider;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.model.Expense;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.repository.ExpenseRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.model.JournalEntry;
import org.sspd.servicemgmt.journaloption.entry.repository.JournalEntryRepository;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.model.Sale;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalBackfillService {

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountResolver accountResolver;
    private final JournalWriter journalWriter;
    private final ObjectProvider<JournalBackfillService> selfProvider;

    public Map<String, Integer> backfillAll() {
        int sales      = backfillSales();
        int purchases  = backfillPurchases();
        int expenses   = backfillExpenses();
        int deliveryReclassifications = reconcileLegacyDeliveryIncome();
        log.info("Journal backfill completed: sales={}, purchases={}, expenses={}, deliveryReclassifications={}",
                sales, purchases, expenses, deliveryReclassifications);
        return Map.of("sales", sales, "purchases", purchases, "expenses", expenses,
                "deliveryReclassifications", deliveryReclassifications);
    }

    /**
     * Makes journals written by pre-INC-012 WAR files compatible with the current
     * chart of accounts. Safe to run repeatedly: each sale has one stable reclass
     * reference, and voided legacy sales have that correction reversed.
     */
    public int reconcileLegacyDeliveryIncome() {
        int count = 0;
        for (Integer saleId : saleRepository.findAll().stream().map(Sale::getId).toList()) {
            try {
                if (selfProvider.getObject().reconcileLegacyDeliveryIncomeForSale(saleId)) count++;
            } catch (Exception e) {
                log.warn("Legacy delivery reconciliation skipped sale ID {}: {}", saleId, e.getMessage(), e);
            }
        }
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reconcileLegacyDeliveryIncomeForSale(Integer saleId) {
            Sale sale = saleRepository.findById(saleId).orElse(null);
            if (sale == null) return false;
            BigDecimal delivery = safe(sale.getDeliveryCharge());
            if (delivery.signum() <= 0 || sale.getId() == null) return false;

            String reclassRef = "DELIVERY-RECLASS-" + sale.getId();
            if (Boolean.TRUE.equals(sale.getVoided())) {
                if (journalWriter.hasActiveReference(reclassRef)) {
                    journalWriter.reverseByReferenceNo(reclassRef, "system",
                            "Legacy WAR sale was voided");
                    return true;
                }
                return false;
            }

            if (journalWriter.hasActiveReference(reclassRef)) return false;
            JournalEntry original = journalEntryRepository.findByReferenceNo(sale.getSaleCode()).orElse(null);
            if (original == null || "REVERSED".equals(original.getStatus())) return false;

            boolean alreadySplit = original.getDetails().stream().anyMatch(detail ->
                    detail.getAccount() != null
                            && "INC-012".equals(detail.getAccount().getCode())
                            && safe(detail.getCredit()).signum() > 0);
            if (alreadySplit) return false;

            BigDecimal productSalesCredit = original.getDetails().stream()
                    .filter(detail -> detail.getAccount() != null
                            && "INC-002".equals(detail.getAccount().getCode()))
                    .map(detail -> safe(detail.getCredit()).subtract(safe(detail.getDebit())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (productSalesCredit.compareTo(delivery) < 0) {
                log.warn("Legacy delivery reclassification skipped sale {}: delivery {} exceeds Product Sales credit {}",
                        sale.getSaleCode(), delivery, productSalesCredit);
                return false;
            }

            JournalEntryDTO correction = new JournalEntryDTO();
            correction.setReferenceNo(reclassRef);
            correction.setEntryDate(sale.getSaleDate() != null ? sale.getSaleDate() : LocalDateTime.now());
            correction.setDescription("Legacy WAR delivery income reclassification - " + sale.getSaleCode());
            correction.setStaffId(sale.getStaff() != null ? sale.getStaff().getId() : null);
            correction.setDetails(List.of(
                    journalLine(accountResolver.sales(), delivery, BigDecimal.ZERO),
                    journalLine(accountResolver.deliveryIncome(), BigDecimal.ZERO, delivery)));
            journalWriter.write(correction);
            return true;
    }

    // ── Sales ──────────────────────────────────────────────────────────────────

    private int backfillSales() {
        int count = 0;
        for (Integer saleId : saleRepository.findAll().stream().map(Sale::getId).toList()) {
            try {
                if (selfProvider.getObject().backfillSaleById(saleId)) count++;
            } catch (Exception e) {
                log.warn("Backfill skipped sale ID {}: {}", saleId, e.getMessage(), e);
            }
        }
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean backfillSaleById(Integer saleId) {
        Sale sale = saleRepository.findById(saleId).orElse(null);
        if (sale == null || sale.getSaleCode() == null || "PENDING".equals(sale.getSaleCode())
                || Boolean.TRUE.equals(sale.getVoided())) return false;
        boolean changed = false;
        if (journalEntryRepository.findByReferenceNo(sale.getSaleCode()).isEmpty()) {
            backfillSale(sale);
            changed = true;
        }
        String cogsReference = sale.getSaleCode() + "-COGS";
        if (journalEntryRepository.findByReferenceNo(cogsReference).isEmpty()) {
            BigDecimal totalCost = sale.getDetails().stream()
                    .map(detail -> safe(detail.getCostPriceSnapshot())
                            .multiply(BigDecimal.valueOf(detail.getQty() != null ? detail.getQty() : 0)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalCost.signum() > 0) {
                writeJournal(cogsReference, sale.getSaleDate(),
                        "Backfill: Inventory cost recognition - " + sale.getSaleCode(),
                        sale.getStaff() != null ? sale.getStaff().getId() : null,
                        List.of(journalLine(accountResolver.cogs(), totalCost, BigDecimal.ZERO),
                                journalLine(accountResolver.inventory(), BigDecimal.ZERO, totalCost)));
                changed = true;
            }
        }
        return changed;
    }

    private void backfillSale(Sale sale) {
        BigDecimal net  = safe(sale.getNetAmount());
        BigDecimal paid = safe(sale.getPaidAmount());
        BigDecimal due  = safe(sale.getDueAmount());
        if (net.compareTo(BigDecimal.ZERO) == 0) return;

        List<JournalDetailDTO> details = new java.util.ArrayList<>();

        // DR Cash/Bank per payment transaction
        List<PaymentTransaction> txns = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(sale.getId(), ReferenceType.Sale);

        if (!txns.isEmpty()) {
            for (PaymentTransaction txn : txns) {
                ChartOfAccount cashAcct = (txn.getPaymentMethod() != null && txn.getPaymentMethod().getAccount() != null)
                        ? txn.getPaymentMethod().getAccount()
                        : accountResolver.cash();
                details.add(journalLine(cashAcct, safe(txn.getAmount()), BigDecimal.ZERO));
            }
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            details.add(journalLine(accountResolver.cash(), paid, BigDecimal.ZERO));
        }

        // DR Accounts Receivable
        if (due.compareTo(BigDecimal.ZERO) > 0) {
            details.add(journalLine(accountResolver.receivable(), due, BigDecimal.ZERO));
        }

        // CR product sales, delivery income and output tax separately.
        BigDecimal tax = safe(sale.getTaxAmount()).min(net);
        BigDecimal delivery = safe(sale.getDeliveryCharge()).min(net.subtract(tax));
        details.add(journalLine(accountResolver.sales(), BigDecimal.ZERO, net.subtract(tax).subtract(delivery)));
        if (delivery.compareTo(BigDecimal.ZERO) > 0) {
            details.add(journalLine(accountResolver.deliveryIncome(), BigDecimal.ZERO, delivery));
        }
        if (tax.compareTo(BigDecimal.ZERO) > 0) {
            details.add(journalLine(accountResolver.taxPayable(), BigDecimal.ZERO, tax));
        }
        writeJournal(sale.getSaleCode(), sale.getSaleDate(), "Backfill: Product Sale - " + sale.getSaleCode(),
                sale.getStaff() != null ? sale.getStaff().getId() : null, details);
    }

    // ── Purchases ──────────────────────────────────────────────────────────────

    private int backfillPurchases() {
        int count = 0;
        for (Integer purchaseId : purchaseRepository.findAll().stream().map(Purchase::getId).toList()) {
            try {
                if (selfProvider.getObject().backfillPurchaseById(purchaseId)) count++;
            } catch (Exception e) {
                log.warn("Backfill skipped purchase ID {}: {}", purchaseId, e.getMessage(), e);
            }
        }
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean backfillPurchaseById(Integer purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId).orElse(null);
        if (purchase == null || purchase.getPurchaseCode() == null
                || purchase.getStatus() == org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CANCELLED
                || journalEntryRepository.findByReferenceNo(purchase.getPurchaseCode()).isPresent()) return false;
        backfillPurchase(purchase);
        return true;
    }

    private void backfillPurchase(Purchase purchase) {
        BigDecimal total = safe(purchase.getNetAmount());
        BigDecimal paid  = safe(purchase.getPaidAmount());
        BigDecimal due   = safe(purchase.getDueAmount());
        if (total.compareTo(BigDecimal.ZERO) == 0) return;

        List<JournalDetailDTO> details = new java.util.ArrayList<>();

        BigDecimal tax = safe(purchase.getTaxAmount()).min(total);
        BigDecimal purchaseCost = total.subtract(tax);
        if (purchaseCost.signum() > 0)
            details.add(journalLine(accountResolver.inventory(), purchaseCost, BigDecimal.ZERO));
        if (tax.signum() > 0)
            details.add(journalLine(accountResolver.inputTaxReceivable(), tax, BigDecimal.ZERO));

        // CR Accounts Payable
        if (due.compareTo(BigDecimal.ZERO) > 0) {
            details.add(journalLine(accountResolver.payable(), BigDecimal.ZERO, due));
        }

        // CR Cash/Bank per payment transaction
        List<PaymentTransaction> txns = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(purchase.getId(), ReferenceType.Purchase);

        if (!txns.isEmpty()) {
            for (PaymentTransaction txn : txns) {
                ChartOfAccount cashAcct = (txn.getPaymentMethod() != null && txn.getPaymentMethod().getAccount() != null)
                        ? txn.getPaymentMethod().getAccount()
                        : accountResolver.cash();
                details.add(journalLine(cashAcct, BigDecimal.ZERO, safe(txn.getAmount())));
            }
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            details.add(journalLine(accountResolver.cash(), BigDecimal.ZERO, paid));
        }
        writeJournal(purchase.getPurchaseCode(), purchase.getPurchaseDate(),
                "Backfill: Purchase - " + purchase.getPurchaseCode(),
                purchase.getStaff() != null ? purchase.getStaff().getId() : null, details);
    }

    // ── Expenses ───────────────────────────────────────────────────────────────

    private int backfillExpenses() {
        int count = 0;
        for (Integer expenseId : expenseRepository.findAll().stream().map(Expense::getId).toList()) {
            try {
                if (selfProvider.getObject().backfillExpenseById(expenseId)) count++;
            } catch (Exception e) {
                log.warn("Backfill skipped expense ID {}: {}", expenseId, e.getMessage(), e);
            }
        }
        return count;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean backfillExpenseById(Integer expenseId) {
        Expense expense = expenseRepository.findById(expenseId).orElse(null);
        if (expense == null || expense.getExpenseCode() == null
                || journalEntryRepository.findByReferenceNo(expense.getExpenseCode()).isPresent()) return false;
        backfillExpense(expense);
        return true;
    }

    private void backfillExpense(Expense expense) {
        BigDecimal amount = safe(expense.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) == 0) return;

        ChartOfAccount cashAcct = (expense.getPaymentMethod() != null && expense.getPaymentMethod().getAccount() != null)
                ? expense.getPaymentMethod().getAccount()
                : accountResolver.cash();
        writeJournal(expense.getExpenseCode(), expense.getExpenseDate(),
                "Backfill: Expense - " + expense.getExpenseCode(),
                expense.getStaff() != null ? expense.getStaff().getId() : null,
                List.of(journalLine(expense.getAccount(), amount, BigDecimal.ZERO),
                        journalLine(cashAcct, BigDecimal.ZERO, amount)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private JournalDetailDTO journalLine(ChartOfAccount account, BigDecimal debit, BigDecimal credit) {
        JournalDetailDTO detail = new JournalDetailDTO();
        detail.setAccountId(account.getId());
        detail.setDebit(debit);
        detail.setCredit(credit);
        return detail;
    }

    private void writeJournal(String referenceNo, LocalDateTime entryDate, String description,
                              Integer staffId, List<JournalDetailDTO> details) {
        JournalEntryDTO journal = new JournalEntryDTO();
        journal.setReferenceNo(referenceNo);
        journal.setEntryDate(entryDate != null ? entryDate : LocalDateTime.now());
        journal.setDescription(description);
        journal.setStaffId(staffId);
        journal.setDetails(details);
        journalWriter.write(journal);
    }
}
