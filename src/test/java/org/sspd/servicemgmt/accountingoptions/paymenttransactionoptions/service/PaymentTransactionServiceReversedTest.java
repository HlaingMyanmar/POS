package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper.PaymentTransactionMapper;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentTransactionServiceReversedTest {

    @Test
    void saveInternalDefaultsNullReversedToFalse() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        PaymentMethod method = new PaymentMethod();
        method.setId(3);
        when(methods.findById(3)).thenReturn(Optional.of(method));
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(1);
            }
            return tx;
        });

        PaymentTransactionService service = new PaymentTransactionService(
                transactions,
                methods,
                PaymentTransactionMapper.INSTANCE,
                mock(SimpMessagingTemplate.class),
                mock(PurchaseRepository.class),
                mock(SupplierRepository.class),
                mock(SaleRepository.class),
                mock(SaleReturnRepository.class),
                mock(ServiceJobRepository.class),
                mock(JournalWriter.class),
                mock(org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator.class)
        );

        PaymentTransactionDTO dto = new PaymentTransactionDTO();
        dto.setPaymentMethodId(3);
        dto.setAmount(BigDecimal.TEN);
        dto.setReferenceType("Opening_Balance");
        dto.setReversed(null);

        PaymentTransactionDTO saved = service.saveInternalTransaction(dto);

        assertEquals(Boolean.FALSE, saved.getReversed());
        assertFalse(Boolean.TRUE.equals(saved.getReversed()));
    }
}
