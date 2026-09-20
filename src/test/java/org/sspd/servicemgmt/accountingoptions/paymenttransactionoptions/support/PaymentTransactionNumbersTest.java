package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.support;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentTransactionNumbersTest {

    @Test
    void saveAssignsTxnFromPersistedIdInsteadOfCount() {
        PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
        AtomicInteger ids = new AtomicInteger(41);
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(ids.getAndIncrement());
            }
            return tx;
        });

        PaymentTransaction first = new PaymentTransaction();
        PaymentTransaction second = new PaymentTransaction();
        PaymentTransaction savedFirst = PaymentTransactionNumbers.save(repository, first);
        PaymentTransaction savedSecond = PaymentTransactionNumbers.save(repository, second);

        assertEquals("TXN-000041", savedFirst.getTransactionNo());
        assertEquals("TXN-000042", savedSecond.getTransactionNo());
        verify(repository, times(4)).save(any(PaymentTransaction.class));
    }

    @Test
    void saveKeepsCallerProvidedNumber() {
        PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            tx.setId(9);
            return tx;
        });

        PaymentTransaction tx = new PaymentTransaction();
        tx.setTransactionNo("  KBZ-1  ");
        PaymentTransaction saved = PaymentTransactionNumbers.save(repository, tx);

        assertEquals("KBZ-1", saved.getTransactionNo());
        verify(repository, times(1)).save(any(PaymentTransaction.class));
    }

    @Test
    void blankToNullTreatsWhitespaceAsMissing() {
        assertNull(PaymentTransactionNumbers.blankToNull("  "));
        assertEquals("A-1", PaymentTransactionNumbers.blankToNull(" A-1 "));
        assertEquals("CPTX-000008", PaymentTransactionNumbers.documentNo("CPTX", 8));
    }
}
