package org.sspd.servicemgmt.journaloption.entry.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository.AccountBalanceRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.mapper.JournalMapper;
import org.sspd.servicemgmt.journaloption.entry.model.JournalEntry;
import org.sspd.servicemgmt.journaloption.entry.repository.JournalEntryRepository;
import org.sspd.servicemgmt.staffoptions.repository.StaffRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalWriterFiscalYearTest {

    @Test
    void refreshesStaleManagedBalanceBeforeAddingJournalDelta() {
        AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
        var entityManager = mock(jakarta.persistence.EntityManager.class);
        ChartOfAccount account = new ChartOfAccount();
        account.setId(10);
        account.setAccountType(AccountType.Asset);
        AccountBalance stale = new AccountBalance(1, account, "2024", BigDecimal.ZERO,
                new BigDecimal("100"), LocalDateTime.now());
        when(balances.findForUpdate(10, "2024")).thenReturn(Optional.of(stale));
        org.mockito.Mockito.doAnswer(invocation -> {
            stale.setCurrentBalance(new BigDecimal("130"));
            return null;
        }).when(entityManager).refresh(stale, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        JournalWriter writer = new JournalWriter(mock(JournalEntryRepository.class), mock(JournalDetailRepository.class),
                balances, mock(ChartOfAccountRepository.class), mock(StaffRepository.class), mock(JournalMapper.class),
                mock(SimpMessagingTemplate.class), mock(AccountingPeriodGuard.class));
        org.springframework.test.util.ReflectionTestUtils.setField(writer, "entityManager", entityManager);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(writer, "updateAccountBalance", account,
                new BigDecimal("20"), BigDecimal.ZERO, LocalDateTime.of(2024, 6, 1, 0, 0));
        assertEquals(new BigDecimal("150"), stale.getCurrentBalance());
        var ordered = org.mockito.Mockito.inOrder(entityManager, balances);
        ordered.verify(entityManager).flush();
        ordered.verify(balances).findForUpdate(10, "2024");
        ordered.verify(entityManager).refresh(stale, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        ordered.verify(balances).save(stale);
    }

    @Test
    void historicalJournalUpdatesItsOwnFiscalYearBalance() {
        JournalEntryRepository journals = mock(JournalEntryRepository.class);
        JournalDetailRepository details = mock(JournalDetailRepository.class);
        AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
        ChartOfAccountRepository accounts = mock(ChartOfAccountRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        JournalMapper mapper = mock(JournalMapper.class);

        ChartOfAccount cash = new ChartOfAccount();
        cash.setId(10);
        cash.setAccountType(AccountType.Asset);

        LocalDateTime historicalDate = LocalDateTime.of(2024, 6, 15, 10, 30);
        JournalEntry entity = JournalEntry.builder()
                .entryDate(historicalDate)
                .referenceNo("HIST-1")
                .build();

        when(journals.findByReferenceNo("HIST-1")).thenReturn(Optional.empty());
        when(mapper.toEntity(any())).thenReturn(entity);
        when(journals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findById(10)).thenReturn(Optional.of(cash));
        when(accounts.findByIdForUpdate(10)).thenReturn(Optional.of(cash));
        when(balances.findForUpdate(10, "2024")).thenReturn(Optional.empty());
        when(mapper.toDto(any(JournalEntry.class))).thenReturn(new JournalEntryDTO());

        JournalDetailDTO debit = new JournalDetailDTO();
        debit.setAccountId(10);
        debit.setDebit(new BigDecimal("500"));
        debit.setCredit(BigDecimal.ZERO);
        JournalDetailDTO credit = new JournalDetailDTO();
        credit.setAccountId(10);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(new BigDecimal("500"));

        JournalEntryDTO dto = new JournalEntryDTO();
        dto.setReferenceNo("HIST-1");
        dto.setEntryDate(historicalDate);
        dto.setDetails(List.of(debit, credit));

        JournalWriter writer = new JournalWriter(journals, details, balances, accounts, staff, mapper,
                mock(SimpMessagingTemplate.class), mock(AccountingPeriodGuard.class));
        org.springframework.test.util.ReflectionTestUtils.setField(writer, "entityManager", mock(jakarta.persistence.EntityManager.class));
        writer.write(dto);

        verify(accounts, org.mockito.Mockito.times(1)).findByIdForUpdate(10);

        ArgumentCaptor<AccountBalance> captor = ArgumentCaptor.forClass(AccountBalance.class);
        verify(balances, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("2024", captor.getAllValues().get(0).getFiscalYear());
        assertEquals("2024", captor.getAllValues().get(1).getFiscalYear());
    }
}
