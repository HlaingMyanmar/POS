package org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.journaloption.detail.model.JournalDetail;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentBalanceValidator {

    private final JournalDetailRepository journalDetailRepository;

    /**
     * Checks if the payment method has sufficient balance for the given amount.
     * Only validates for cash/bank type accounts (assuming account type is Asset).
     * Throws RuntimeException if insufficient balance.
     */
    public void validateSufficientBalance(PaymentMethod paymentMethod, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // No need to check for zero or negative
        }

        // Assume cash/bank are asset accounts, check balance
        BigDecimal balance = getAccountBalance(paymentMethod.getAccount().getId());

        if (balance.compareTo(amount) < 0) {
            throw new RuntimeException(
                "Insufficient balance in " + paymentMethod.getMethodName() +
                ". Available: " + balance + ", Required: " + amount
            );
        }
    }

    /**
     * Calculates the current balance of an account: sum(debit) - sum(credit)
     */
    private BigDecimal getAccountBalance(Integer accountId) {
        List<JournalDetail> details = journalDetailRepository.findByAccountId(accountId);
        BigDecimal debitSum = details.stream()
                .map(d -> d.getDebit() != null ? d.getDebit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditSum = details.stream()
                .map(d -> d.getCredit() != null ? d.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.debug("Payment account balance calculated: accountId={}, debitSum={}, creditSum={}",
                accountId, debitSum, creditSum);
        return debitSum.subtract(creditSum);
    }
}