package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.AccountTransferDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper.PaymentTransactionMapper;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentTransactionTransferBalanceTest {

    @Test
    void transfer_rejectsWhenFromAccountHasInsufficientBalance() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        PaymentBalanceValidator balanceValidator = mock(PaymentBalanceValidator.class);
        JournalWriter journalWriter = mock(JournalWriter.class);

        ChartOfAccount cash = new ChartOfAccount();
        cash.setId(10);
        ChartOfAccount bank = new ChartOfAccount();
        bank.setId(20);

        PaymentMethod from = new PaymentMethod();
        from.setId(1);
        from.setMethodName("Cash");
        from.setAccount(cash);

        PaymentMethod to = new PaymentMethod();
        to.setId(2);
        to.setMethodName("KBZ");
        to.setAccount(bank);

        when(methods.findById(1)).thenReturn(Optional.of(from));
        when(methods.findById(2)).thenReturn(Optional.of(to));
        doThrow(new RuntimeException("Cash တွင် လက်ကျန်မလောက်ပါ။ ကျန်ငွေ: 0 Ks၊ လွှဲမည့်ပမာဏ: 5000 Ks"))
                .when(balanceValidator).validateSufficientBalance(eq(from), eq(new BigDecimal("5000")));

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
                journalWriter,
                balanceValidator
        );

        AccountTransferDTO dto = new AccountTransferDTO();
        dto.setFromPaymentMethodId(1);
        dto.setToPaymentMethodId(2);
        dto.setAmount(new BigDecimal("5000"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.transfer(dto));
        assertTrue(ex.getMessage().contains("လက်ကျန်မလောက်"));
        verify(transactions, never()).save(any());
        verify(journalWriter, never()).write(any());
    }
}
