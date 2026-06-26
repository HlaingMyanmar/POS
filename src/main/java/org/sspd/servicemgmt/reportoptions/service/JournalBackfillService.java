package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountResolver;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.model.Expense;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.repository.ExpenseRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.journaloption.detail.model.JournalDetail;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;
import org.sspd.servicemgmt.journaloption.entry.model.JournalEntry;
import org.sspd.servicemgmt.journaloption.entry.repository.JournalEntryRepository;
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
    private final JournalDetailRepository journalDetailRepository;
    private final AccountResolver accountResolver;

    @Transactional
    public Map<String, Integer> backfillAll() {
        int sales      = backfillSales();
        int purchases  = backfillPurchases();
        int expenses   = backfillExpenses();
        log.info("Journal backfill completed: sales={}, purchases={}, expenses={}", sales, purchases, expenses);
        return Map.of("sales", sales, "purchases", purchases, "expenses", expenses);
    }

    // ── Sales ──────────────────────────────────────────────────────────────────

    private int backfillSales() {
        int count = 0;
        for (Sale sale : saleRepository.findAll()) {
            String ref = sale.getSaleCode();
            if (ref == null || ref.equals("PENDING")) continue;
            if (journalEntryRepository.findByReferenceNo(ref).isPresent()) continue;

            try {
                backfillSale(sale);
                count++;
            } catch (Exception e) {
                log.warn("Backfill skipped sale {}: {}", ref, e.getMessage(), e);
            }
        }
        return count;
    }

    private void backfillSale(Sale sale) {
        BigDecimal net  = safe(sale.getNetAmount());
        BigDecimal paid = safe(sale.getPaidAmount());
        BigDecimal due  = safe(sale.getDueAmount());
        if (net.compareTo(BigDecimal.ZERO) == 0) return;

        JournalEntry entry = journalEntryRepository.save(JournalEntry.builder()
                .referenceNo(sale.getSaleCode())
                .entryDate(sale.getSaleDate() != null ? sale.getSaleDate() : LocalDateTime.now())
                .description("Backfill: Product Sale - " + sale.getSaleCode())
                .staff(sale.getStaff())
                .build());

        // DR Cash/Bank per payment transaction
        List<PaymentTransaction> txns = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(sale.getId(), ReferenceType.Sale);

        if (!txns.isEmpty()) {
            for (PaymentTransaction txn : txns) {
                ChartOfAccount cashAcct = (txn.getPaymentMethod() != null && txn.getPaymentMethod().getAccount() != null)
                        ? txn.getPaymentMethod().getAccount()
                        : accountResolver.cash();
                saveDetail(entry, cashAcct, safe(txn.getAmount()), BigDecimal.ZERO);
            }
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            saveDetail(entry, accountResolver.cash(), paid, BigDecimal.ZERO);
        }

        // DR Accounts Receivable
        if (due.compareTo(BigDecimal.ZERO) > 0) {
            saveDetail(entry, accountResolver.receivable(), due, BigDecimal.ZERO);
        }

        // CR Sales Revenue
        saveDetail(entry, accountResolver.sales(), BigDecimal.ZERO, net);
    }

    // ── Purchases ──────────────────────────────────────────────────────────────

    private int backfillPurchases() {
        int count = 0;
        for (Purchase purchase : purchaseRepository.findAll()) {
            String ref = purchase.getPurchaseCode();
            if (ref == null) continue;
            if (journalEntryRepository.findByReferenceNo(ref).isPresent()) continue;

            try {
                backfillPurchase(purchase);
                count++;
            } catch (Exception e) {
                log.warn("Backfill skipped purchase {}: {}", ref, e.getMessage(), e);
            }
        }
        return count;
    }

    private void backfillPurchase(Purchase purchase) {
        BigDecimal total = safe(purchase.getTotalAmount());
        BigDecimal paid  = safe(purchase.getPaidAmount());
        BigDecimal due   = safe(purchase.getDueAmount());
        if (total.compareTo(BigDecimal.ZERO) == 0) return;

        JournalEntry entry = journalEntryRepository.save(JournalEntry.builder()
                .referenceNo(purchase.getPurchaseCode())
                .entryDate(purchase.getPurchaseDate() != null ? purchase.getPurchaseDate() : LocalDateTime.now())
                .description("Backfill: Purchase - " + purchase.getPurchaseCode())
                .staff(purchase.getStaff())
                .build());

        // DR Purchases
        saveDetail(entry, accountResolver.purchases(), total, BigDecimal.ZERO);

        // CR Accounts Payable
        if (due.compareTo(BigDecimal.ZERO) > 0) {
            saveDetail(entry, accountResolver.payable(), BigDecimal.ZERO, due);
        }

        // CR Cash/Bank per payment transaction
        List<PaymentTransaction> txns = paymentTransactionRepository
                .findByReferenceIdAndReferenceType(purchase.getId(), ReferenceType.Purchase);

        if (!txns.isEmpty()) {
            for (PaymentTransaction txn : txns) {
                ChartOfAccount cashAcct = (txn.getPaymentMethod() != null && txn.getPaymentMethod().getAccount() != null)
                        ? txn.getPaymentMethod().getAccount()
                        : accountResolver.cash();
                saveDetail(entry, cashAcct, BigDecimal.ZERO, safe(txn.getAmount()));
            }
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            saveDetail(entry, accountResolver.cash(), BigDecimal.ZERO, paid);
        }
    }

    // ── Expenses ───────────────────────────────────────────────────────────────

    private int backfillExpenses() {
        int count = 0;
        for (Expense expense : expenseRepository.findAll()) {
            String ref = expense.getExpenseCode();
            if (ref == null) continue;
            if (journalEntryRepository.findByReferenceNo(ref).isPresent()) continue;

            try {
                backfillExpense(expense);
                count++;
            } catch (Exception e) {
                log.warn("Backfill skipped expense {}: {}", ref, e.getMessage(), e);
            }
        }
        return count;
    }

    private void backfillExpense(Expense expense) {
        BigDecimal amount = safe(expense.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) == 0) return;

        JournalEntry entry = journalEntryRepository.save(JournalEntry.builder()
                .referenceNo(expense.getExpenseCode())
                .entryDate(expense.getExpenseDate() != null ? expense.getExpenseDate() : LocalDateTime.now())
                .description("Backfill: Expense - " + expense.getExpenseCode())
                .staff(expense.getStaff())
                .build());

        // DR Expense account
        saveDetail(entry, expense.getAccount(), amount, BigDecimal.ZERO);

        // CR Cash/Bank
        ChartOfAccount cashAcct = (expense.getPaymentMethod() != null && expense.getPaymentMethod().getAccount() != null)
                ? expense.getPaymentMethod().getAccount()
                : accountResolver.cash();
        saveDetail(entry, cashAcct, BigDecimal.ZERO, amount);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void saveDetail(JournalEntry entry, ChartOfAccount account, BigDecimal debit, BigDecimal credit) {
        journalDetailRepository.save(JournalDetail.builder()
                .journalEntry(entry)
                .account(account)
                .debit(debit != null ? debit : BigDecimal.ZERO)
                .credit(credit != null ? credit : BigDecimal.ZERO)
                .build());
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
