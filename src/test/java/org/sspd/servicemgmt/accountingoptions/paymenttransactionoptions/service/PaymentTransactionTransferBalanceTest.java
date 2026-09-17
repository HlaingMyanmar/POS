package org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.model.PaymentMethod;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.repository.PaymentMethodRepository;
import org.sspd.servicemgmt.accountingoptions.paymentmethodoptions.service.PaymentBalanceValidator;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.AccountTransferDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.mapper.PaymentTransactionMapper;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.cashdraweroptions.service.CashDrawerService;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;
import org.sspd.servicemgmt.purchaseoptions.repository.PurchaseRepository;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.repository.SaleReturnRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;
import org.sspd.servicemgmt.supplieroptions.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        CashDrawerService drawer = mock(CashDrawerService.class);
        stubMethods(methods, cashMethod(1, "Cash"), bankMethod(2, "KBZ"));
        doThrow(new RuntimeException("Cash တွင် လက်ကျန်မလောက်ပါ။ ကျန်ငွေ: 0 Ks၊ လွှဲမည့်ပမာဏ: 5000 Ks"))
                .when(balanceValidator).validateSufficientBalance(any(), eq(new BigDecimal("5000")));

        PaymentTransactionService service = service(transactions, methods, journalWriter, balanceValidator,
                mock(AccountingPeriodGuard.class), drawer);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.transfer(transfer(1, 2, "5000")));
        assertTrue(ex.getMessage().contains("လက်ကျန်မလောက်"));
        verify(transactions, never()).save(any());
        verify(journalWriter, never()).write(any());
        verify(drawer, never()).recordPurchaseCashOut(any(), any(), any(), any());
        verify(drawer, never()).recordPurchaseCashIn(any(), any(), any(), any());
    }

    @Test
    void transfer_rejectsClosedAccountingPeriod() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentBalanceValidator balanceValidator = mock(PaymentBalanceValidator.class);
        JournalWriter journalWriter = mock(JournalWriter.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        doThrow(new IllegalStateException("Accounting period is locked (2024-01-01 to 2024-01-31). Cannot record account transfer."))
                .when(periodGuard).assertOpen(any(LocalDateTime.class), eq("record account transfer"));

        PaymentTransactionService service = service(transactions, mock(PaymentMethodRepository.class),
                journalWriter, balanceValidator, periodGuard, drawer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.transfer(transfer(1, 2, "5000")));
        assertTrue(ex.getMessage().contains("Accounting period is locked"));
        verify(drawer, never()).recordPurchaseCashOut(any(), any(), any(), any());
        verify(drawer, never()).recordPurchaseCashIn(any(), any(), any(), any());
    }

    @Test
    void transfer_cashToBankRecordsDrawerCashOut() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        stubMethods(methods, cashMethod(1, "Cash"), bankMethod(2, "KBZ"));
        stubSaves(transactions);

        PaymentTransactionService service = service(transactions, methods, mock(JournalWriter.class),
                mock(PaymentBalanceValidator.class), mock(AccountingPeriodGuard.class), drawer);

        service.transfer(transfer(1, 2, "5000"));

        verify(drawer).recordPurchaseCashOut(eq(new BigDecimal("5000")), any(), eq("Transfer"), eq(1));
        verify(drawer, never()).recordPurchaseCashIn(any(), any(), any(), any());
    }

    @Test
    void transfer_bankToCashRecordsDrawerCashIn() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        stubMethods(methods, bankMethod(1, "KBZ"), cashMethod(2, "Cash"));
        stubSaves(transactions);

        PaymentTransactionService service = service(transactions, methods, mock(JournalWriter.class),
                mock(PaymentBalanceValidator.class), mock(AccountingPeriodGuard.class), drawer);

        service.transfer(transfer(1, 2, "5000"));

        verify(drawer).recordPurchaseCashIn(eq(new BigDecimal("5000")), any(), eq("Transfer"), eq(2));
        verify(drawer, never()).recordPurchaseCashOut(any(), any(), any(), any());
    }

    @Test
    void transfer_bankToBankDoesNotMoveDrawer() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        stubMethods(methods, bankMethod(1, "KBZ"), bankMethod(2, "Wave"));
        stubSaves(transactions);

        PaymentTransactionService service = service(transactions, methods, mock(JournalWriter.class),
                mock(PaymentBalanceValidator.class), mock(AccountingPeriodGuard.class), drawer);

        service.transfer(transfer(1, 2, "5000"));

        verify(drawer, never()).recordPurchaseCashOut(any(), any(), any(), any());
        verify(drawer, never()).recordPurchaseCashIn(any(), any(), any(), any());
    }

    @Test
    void transfer_rejectsWhenStaffIsMissing() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        JournalWriter journalWriter = mock(JournalWriter.class);
        CashDrawerService drawer = mock(CashDrawerService.class);
        stubMethods(methods, cashMethod(1, "Cash"), bankMethod(2, "KBZ"));
        StaffRepository staff = mock(StaffRepository.class);
        when(staff.existsById(any())).thenReturn(false);

        PaymentTransactionService service = service(transactions, methods, journalWriter,
                mock(PaymentBalanceValidator.class), mock(AccountingPeriodGuard.class), drawer, staff);
        AccountTransferDTO dto = transfer(1, 2, "5000");
        dto.setStaffId(null);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> service.transfer(dto));
        assertTrue(thrown.getMessage().toLowerCase().contains("staff"));
        verify(transactions, never()).save(any());
        verify(journalWriter, never()).write(any());
        verify(drawer, never()).recordPurchaseCashOut(any(), any(), any(), any());
    }

    @Test
    void transfer_writesJournalWithResolvedStaff() {
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        PaymentMethodRepository methods = mock(PaymentMethodRepository.class);
        JournalWriter journalWriter = mock(JournalWriter.class);
        stubMethods(methods, cashMethod(1, "Cash"), bankMethod(2, "KBZ"));
        stubSaves(transactions);

        PaymentTransactionService service = service(transactions, methods, journalWriter,
                mock(PaymentBalanceValidator.class), mock(AccountingPeriodGuard.class), mock(CashDrawerService.class));
        service.transfer(transfer(1, 2, "5000"));

        org.mockito.ArgumentCaptor<JournalEntryDTO> captor = org.mockito.ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(journalWriter).write(captor.capture());
        assertEquals(Integer.valueOf(9), captor.getValue().getStaffId());
    }

    private static PaymentTransactionService service(PaymentTransactionRepository transactions,
                                                     PaymentMethodRepository methods,
                                                     JournalWriter journalWriter,
                                                     PaymentBalanceValidator balanceValidator,
                                                     AccountingPeriodGuard periodGuard,
                                                     CashDrawerService drawer) {
        StaffRepository staff = mock(StaffRepository.class);
        when(staff.existsById(9)).thenReturn(true);
        return service(transactions, methods, journalWriter, balanceValidator, periodGuard, drawer, staff);
    }

    private static PaymentTransactionService service(PaymentTransactionRepository transactions,
                                                     PaymentMethodRepository methods,
                                                     JournalWriter journalWriter,
                                                     PaymentBalanceValidator balanceValidator,
                                                     AccountingPeriodGuard periodGuard,
                                                     CashDrawerService drawer,
                                                     StaffRepository staff) {
        return new PaymentTransactionService(
                transactions, methods, PaymentTransactionMapper.INSTANCE,
                mock(SimpMessagingTemplate.class), mock(PurchaseRepository.class),
                mock(SupplierRepository.class), mock(SaleRepository.class),
                mock(SaleReturnRepository.class), mock(ServiceJobRepository.class),
                journalWriter, balanceValidator, periodGuard, drawer, staff, mock(UserRepository.class));
    }

    private static void stubMethods(PaymentMethodRepository methods, PaymentMethod from, PaymentMethod to) {
        when(methods.findById(from.getId())).thenReturn(Optional.of(from));
        when(methods.findById(to.getId())).thenReturn(Optional.of(to));
    }

    private static void stubSaves(PaymentTransactionRepository transactions) {
        AtomicInteger ids = new AtomicInteger(1);
        when(transactions.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            if (tx.getId() == null) tx.setId(ids.getAndIncrement());
            if (tx.getTransactionNo() == null) tx.setTransactionNo("TXN-" + tx.getId());
            return tx;
        });
    }

    private static AccountTransferDTO transfer(int fromId, int toId, String amount) {
        AccountTransferDTO dto = new AccountTransferDTO();
        dto.setFromPaymentMethodId(fromId);
        dto.setToPaymentMethodId(toId);
        dto.setAmount(new BigDecimal(amount));
        dto.setStaffId(9);
        return dto;
    }

    private static PaymentMethod cashMethod(int id, String name) {
        ChartOfAccount cash = new ChartOfAccount();
        cash.setId(10);
        cash.setCode(AccountCode.CASH);
        PaymentMethod method = new PaymentMethod();
        method.setId(id);
        method.setMethodName(name);
        method.setAccount(cash);
        return method;
    }

    private static PaymentMethod bankMethod(int id, String name) {
        ChartOfAccount bank = new ChartOfAccount();
        bank.setId(20 + id);
        bank.setCode(AccountCode.BANK_KBZ);
        PaymentMethod method = new PaymentMethod();
        method.setId(id);
        method.setMethodName(name);
        method.setAccount(bank);
        return method;
    }
}
