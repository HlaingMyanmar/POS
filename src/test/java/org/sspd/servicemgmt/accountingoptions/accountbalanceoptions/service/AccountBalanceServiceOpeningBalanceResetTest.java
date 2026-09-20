package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.dto.AccountBalanceDTO;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.mapper.AccountBalanceMapper;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository.AccountBalanceRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.dto.PaymentTransactionDTO;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.PaymentTransaction;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.model.ReferenceType;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.repository.PaymentTransactionRepository;
import org.sspd.servicemgmt.accountingoptions.paymenttransactionoptions.service.PaymentTransactionService;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.service.JournalWriter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountBalanceServiceOpeningBalanceResetTest {

    @Test
    void resetUsesNewJournalAndTransactionNumbersAndReversesPriorRows() {
        AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
        AccountBalanceMapper mapper = mock(AccountBalanceMapper.class);
        JournalWriter journals = mock(JournalWriter.class);
        PaymentTransactionService payments = mock(PaymentTransactionService.class);
        PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
        ChartOfAccountRepository accounts = mock(ChartOfAccountRepository.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);

        ChartOfAccount cash = ChartOfAccount.builder().id(10).accountName("Cash")
                .accountType(AccountType.Asset).code("AST-001").build();
        ChartOfAccount equity = ChartOfAccount.builder().id(90).accountName("Share Capital")
                .accountType(AccountType.Equity).code("EQU-001").build();
        when(accounts.findById(10)).thenReturn(Optional.of(cash));
        when(accounts.findByCode(AccountCode.SHARE_CAPITAL)).thenReturn(Optional.of(equity));

        PaymentTransaction prior = new PaymentTransaction();
        prior.setId(3);
        prior.setTransactionNo("OPN-ACCT-10");
        prior.setReferenceType(ReferenceType.Opening_Balance);
        prior.setReversed(false);
        when(transactions.findByTransactionNo("OPN-ACCT-10")).thenReturn(Optional.of(prior));
        when(transactions.findByTransactionNoStartingWith("OPN-ACCT-10-")).thenReturn(List.of());

        AccountBalance stored = new AccountBalance();
        stored.setAccount(cash);
        stored.setFiscalYear(String.valueOf(LocalDateTime.now().getYear()));
        when(balances.findByAccountIdAndFiscalYear(eq(10), any())).thenReturn(Optional.of(stored));
        when(balances.save(stored)).thenReturn(stored);
        when(balances.findByAccountId(10)).thenReturn(Optional.of(stored));
        when(mapper.toDto(stored)).thenReturn(new AccountBalanceDTO());

        AccountBalanceService service = new AccountBalanceService(
                balances, mapper, mock(SimpMessagingTemplate.class), journals,
                payments, transactions, accounts, periodGuard);

        service.setOpeningBalance(10, new BigDecimal("5000"), 1, 4);

        verify(journals).reverseByReferenceNo("OPN-ACCT-10", "system", "Reset opening balance");
        verify(journals).reverseByReferencePrefix("OPN-ACCT-10-", "system", "Reset opening balance");
        assertTrue(Boolean.TRUE.equals(prior.getReversed()));
        assertEquals("Reset opening balance", prior.getReversalReason());

        ArgumentCaptor<JournalEntryDTO> journalCaptor = ArgumentCaptor.forClass(JournalEntryDTO.class);
        verify(journals).write(journalCaptor.capture());
        String journalRef = journalCaptor.getValue().getReferenceNo();
        assertNotEquals("OPN-ACCT-10", journalRef);
        assertTrue(journalRef.startsWith("OPN-ACCT-10-"));

        ArgumentCaptor<PaymentTransactionDTO> payCaptor = ArgumentCaptor.forClass(PaymentTransactionDTO.class);
        verify(payments).saveOpeningBalanceTransaction(payCaptor.capture());
        assertEquals(journalRef, payCaptor.getValue().getTransactionNo());
        assertEquals(Integer.valueOf(10), payCaptor.getValue().getReferenceId());
    }

    @Test
    void rejectsClosedAccountingPeriod() {
        JournalWriter journals = mock(JournalWriter.class);
        PaymentTransactionService payments = mock(PaymentTransactionService.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(any(LocalDateTime.class), eq("set opening balance"));

        AccountBalanceService service = new AccountBalanceService(
                mock(AccountBalanceRepository.class), mock(AccountBalanceMapper.class),
                mock(SimpMessagingTemplate.class), journals, payments,
                mock(PaymentTransactionRepository.class), mock(ChartOfAccountRepository.class), periodGuard);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.setOpeningBalance(10, new BigDecimal("5000"), 1, 4));
        assertTrue(thrown.getMessage().contains("Accounting period is locked"));
        verify(journals, never()).write(any());
        verify(payments, never()).saveOpeningBalanceTransaction(any());
    }
}
