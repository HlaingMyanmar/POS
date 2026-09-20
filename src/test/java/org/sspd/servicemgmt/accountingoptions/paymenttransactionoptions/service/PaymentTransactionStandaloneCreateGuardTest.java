package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper.PaymentTransactionMapper;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentTransactionStandaloneCreateGuardTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "Sale",
            "Service",
            "Sale_Return",
            "Purchase",
            "Purchase_Return",
            "Debt_Payment",
            "Customer_Order",
            "Transfer",
            "Opening_Balance"
    })
    void publicSaveRejectsStandaloneRowsForAnyReference(String referenceType) {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentTransactionService service = new PaymentTransactionService(
                transactions,
                mock(PaymentMethodRepository.class),
                PaymentTransactionMapper.INSTANCE,
                mock(SimpMessagingTemplate.class),
                mock(PurchaseRepository.class),
                mock(SupplierRepository.class),
                mock(SaleRepository.class),
                mock(SaleReturnRepository.class),
                mock(ServiceJobRepository.class),
                mock(JournalWriter.class),
                mock(PaymentBalanceValidator.class),
                mock(org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard.class),
                mock(org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService.class),
                mock(org.sspd.servicemgmt.staffoptions.repository.StaffRepository.class),
                mock(org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository.class)
        );

        PaymentTransactionDTO dto = new PaymentTransactionDTO();
        dto.setReferenceId(11);
        dto.setReferenceType(referenceType);
        dto.setPaymentMethodId(3);
        dto.setAmount(new BigDecimal("20"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.save(dto));
        assertTrue(thrown.getMessage().contains("cannot be created independently"));
        verify(transactions, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sale", "Service"})
    void saveInternalRejectsNonOpeningBalanceRows(String referenceType) {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentTransactionService service = new PaymentTransactionService(
                transactions,
                mock(PaymentMethodRepository.class),
                PaymentTransactionMapper.INSTANCE,
                mock(SimpMessagingTemplate.class),
                mock(PurchaseRepository.class),
                mock(SupplierRepository.class),
                mock(SaleRepository.class),
                mock(SaleReturnRepository.class),
                mock(ServiceJobRepository.class),
                mock(JournalWriter.class),
                mock(PaymentBalanceValidator.class),
                mock(org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard.class),
                mock(org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService.class),
                mock(org.sspd.servicemgmt.staffoptions.repository.StaffRepository.class),
                mock(org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository.class)
        );
        PaymentTransactionDTO dto = new PaymentTransactionDTO();
        dto.setReferenceType(referenceType);
        dto.setPaymentMethodId(3);
        dto.setAmount(new BigDecimal("20"));
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.saveInternalTransaction(dto));
        assertTrue(thrown.getMessage().toLowerCase().contains("opening-balance"));
        verify(transactions, never()).save(any());
    }
}
