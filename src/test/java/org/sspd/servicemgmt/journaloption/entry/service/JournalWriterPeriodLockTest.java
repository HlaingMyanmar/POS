package org.sspd.servicemgmt.journaloption.entry.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository.AccountBalanceRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.journaloption.detail.dto.JournalDetailDTO;
import org.sspd.servicemgmt.journaloption.detail.model.JournalDetail;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalWriterPeriodLockTest {

    private final LocalDateTime closed = LocalDateTime.of(2024, 1, 15, 10, 0);

    @Test
    void writeRejectsClosedAccountingPeriod() {
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        JournalEntryRepository journals = mock(JournalEntryRepository.class);
        JournalWriter writer = writer(journals, periodGuard);
        JournalEntryDTO dto = new JournalEntryDTO();
        dto.setEntryDate(closed);
        dto.setDetails(List.of(line(1, "10", "0"), line(2, "0", "10")));
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(closed, "post journal");

        assertThrows(IllegalStateException.class, () -> writer.write(dto));
        verify(journals, never()).save(any());
    }

    @Test
    void reverseRejectsClosedOriginalPeriodAndDoesNotPostReversal() {
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        JournalEntryRepository journals = mock(JournalEntryRepository.class);
        JournalWriter writer = writer(journals, periodGuard);
        JournalEntry original = JournalEntry.builder()
                .entryDate(closed)
                .referenceNo("INV-1")
                .status("POSTED")
                .details(List.of(JournalDetail.builder().build()))
                .build();
        when(journals.findByReferenceNo("INV-1")).thenReturn(Optional.of(original));
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(eq(closed), contains("reverse journal"));

        assertThrows(IllegalStateException.class, () -> writer.reverseByReferenceNo("INV-1", "admin", "void"));
        verify(journals, never()).save(any());
    }

    private static JournalDetailDTO line(int accountId, String debit, String credit) {
        JournalDetailDTO detail = new JournalDetailDTO();
        detail.setAccountId(accountId);
        detail.setDebit(new BigDecimal(debit));
        detail.setCredit(new BigDecimal(credit));
        return detail;
    }

    private static JournalWriter writer(JournalEntryRepository journals, AccountingPeriodGuard periodGuard) {
        JournalWriter writer = new JournalWriter(
                journals,
                mock(JournalDetailRepository.class),
                mock(AccountBalanceRepository.class),
                mock(ChartOfAccountRepository.class),
                mock(StaffRepository.class),
                mock(JournalMapper.class),
                mock(SimpMessagingTemplate.class),
                periodGuard);
        org.springframework.test.util.ReflectionTestUtils.setField(
                writer, "entityManager", mock(jakarta.persistence.EntityManager.class));
        return writer;
    }
}
