package org.sspd.servicemgmt.journaloption.entry.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JournalWriterValidationTest {

    @Test
    void writeRejectsEmptyDetails() {
        JournalEntryRepository journals = mock(JournalEntryRepository.class);
        JournalWriter writer = writer(journals);
        JournalEntryDTO dto = new JournalEntryDTO();
        dto.setDetails(List.of());
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> writer.write(dto));
        assertTrue(thrown.getMessage().toLowerCase().contains("details"));
        verifyNoInteractions(journals);
    }

    @Test
    void writeRejectsNegativeAmountAndDualSidedLine() {
        JournalWriter writer = writer(mock(JournalEntryRepository.class));
        JournalDetailDTO negative = line(1, new BigDecimal("-5"), BigDecimal.ZERO);
        JournalEntryDTO negativeDto = new JournalEntryDTO();
        negativeDto.setDetails(List.of(negative, line(2, BigDecimal.ZERO, new BigDecimal("5"))));
        assertThrows(IllegalArgumentException.class, () -> writer.write(negativeDto));

        JournalDetailDTO both = line(1, new BigDecimal("10"), new BigDecimal("10"));
        JournalEntryDTO bothDto = new JournalEntryDTO();
        bothDto.setDetails(List.of(both));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> writer.write(bothDto));
        assertTrue(thrown.getMessage().toLowerCase().contains("both"));
    }

    @Test
    void writeRejectsMissingAccount() {
        JournalWriter writer = writer(mock(JournalEntryRepository.class));
        JournalDetailDTO missing = new JournalDetailDTO();
        missing.setDebit(new BigDecimal("10"));
        JournalEntryDTO dto = new JournalEntryDTO();
        dto.setDetails(List.of(missing, line(2, BigDecimal.ZERO, new BigDecimal("10"))));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> writer.write(dto));
        assertTrue(thrown.getMessage().toLowerCase().contains("account"));
    }

    @Test
    void writeRejectsAssetCreditWhenLockedBalanceIsInsufficient() {
        JournalEntryRepository journals = mock(JournalEntryRepository.class);
        JournalDetailRepository details = mock(JournalDetailRepository.class);
        ChartOfAccountRepository accounts = mock(ChartOfAccountRepository.class);
        ChartOfAccount cash = ChartOfAccount.builder().id(10).accountName("Cash")
                .accountType(AccountType.Asset).build();
        ChartOfAccount payable = ChartOfAccount.builder().id(8).accountName("Payable")
                .accountType(AccountType.Liability).build();
        when(accounts.findByIdForUpdate(8)).thenReturn(Optional.of(payable));
        when(accounts.findByIdForUpdate(10)).thenReturn(Optional.of(cash));
        when(details.netDebitByAccountId(10)).thenReturn(new BigDecimal("40"));
        JournalWriter writer = new JournalWriter(journals, details, mock(AccountBalanceRepository.class),
                accounts, mock(StaffRepository.class), mock(JournalMapper.class), mock(SimpMessagingTemplate.class),
                mock(AccountingPeriodGuard.class));
        org.springframework.test.util.ReflectionTestUtils.setField(writer, "entityManager",
                mock(jakarta.persistence.EntityManager.class));

        JournalEntryDTO dto = new JournalEntryDTO();
        dto.setDetails(List.of(
                line(8, new BigDecimal("100"), BigDecimal.ZERO),
                line(10, BigDecimal.ZERO, new BigDecimal("100"))));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> writer.write(dto));
        assertTrue(thrown.getMessage().contains("လက်ကျန်မလောက်"));
        assertTrue(thrown.getMessage().contains("40"));
        verifyNoInteractions(journals);
    }

    @Test
    void writeAllowsAssetCreditWhenLockedBalanceCoversAmount() {
        JournalEntryRepository journals = mock(JournalEntryRepository.class);
        JournalDetailRepository details = mock(JournalDetailRepository.class);
        ChartOfAccountRepository accounts = mock(ChartOfAccountRepository.class);
        JournalMapper mapper = mock(JournalMapper.class);
        ChartOfAccount cash = ChartOfAccount.builder().id(10).accountName("Cash")
                .accountType(AccountType.Asset).build();
        ChartOfAccount payable = ChartOfAccount.builder().id(8).accountName("Payable")
                .accountType(AccountType.Liability).build();
        when(accounts.findByIdForUpdate(8)).thenReturn(Optional.of(payable));
        when(accounts.findByIdForUpdate(10)).thenReturn(Optional.of(cash));
        when(accounts.findById(8)).thenReturn(Optional.of(payable));
        when(accounts.findById(10)).thenReturn(Optional.of(cash));
        when(details.netDebitByAccountId(10)).thenReturn(new BigDecimal("100"));
        JournalEntry entity = JournalEntry.builder().entryDate(java.time.LocalDateTime.now()).build();
        when(mapper.toEntity(any())).thenReturn(entity);
        when(journals.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(JournalEntry.class))).thenReturn(new JournalEntryDTO());
        JournalWriter writer = new JournalWriter(journals, details, mock(AccountBalanceRepository.class),
                accounts, mock(StaffRepository.class), mapper, mock(SimpMessagingTemplate.class),
                mock(AccountingPeriodGuard.class));
        org.springframework.test.util.ReflectionTestUtils.setField(writer, "entityManager",
                mock(jakarta.persistence.EntityManager.class));

        JournalEntryDTO dto = new JournalEntryDTO();
        dto.setDetails(List.of(
                line(8, new BigDecimal("100"), BigDecimal.ZERO),
                line(10, BigDecimal.ZERO, new BigDecimal("100"))));
        writer.write(dto);
        verify(journals).save(any());
    }

    private JournalDetailDTO line(int accountId, BigDecimal debit, BigDecimal credit) {
        JournalDetailDTO detail = new JournalDetailDTO();
        detail.setAccountId(accountId);
        detail.setDebit(debit);
        detail.setCredit(credit);
        return detail;
    }

    private JournalWriter writer(JournalEntryRepository journals) {
        JournalWriter writer = new JournalWriter(journals, mock(JournalDetailRepository.class),
                mock(AccountBalanceRepository.class), mock(ChartOfAccountRepository.class),
                mock(StaffRepository.class), mock(JournalMapper.class), mock(SimpMessagingTemplate.class),
                mock(AccountingPeriodGuard.class));
        org.springframework.test.util.ReflectionTestUtils.setField(writer, "entityManager",
                mock(jakarta.persistence.EntityManager.class));
        return writer;
    }
}
