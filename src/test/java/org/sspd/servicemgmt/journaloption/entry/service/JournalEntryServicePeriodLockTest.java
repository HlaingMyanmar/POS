package org.sspd.servicemgmt.journaloption.entry.service;

import org.junit.jupiter.api.Test;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.mapper.JournalMapper;
import org.sspd.servicemgmt.journaloption.entry.repository.JournalEntryRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JournalEntryServicePeriodLockTest {

    @Test
    void saveRejectsClosedAccountingPeriod() {
        JournalWriter writer = mock(JournalWriter.class);
        AccountingPeriodGuard periodGuard = mock(AccountingPeriodGuard.class);
        JournalEntryService service = new JournalEntryService(
                mock(JournalEntryRepository.class), mock(JournalMapper.class), writer, periodGuard);
        JournalEntryDTO dto = new JournalEntryDTO();
        LocalDateTime entryDate = LocalDateTime.of(2024, 1, 15, 10, 0);
        dto.setEntryDate(entryDate);
        doThrow(new IllegalStateException("Accounting period is locked"))
                .when(periodGuard).assertOpen(entryDate, "post manual journal");

        assertThrows(IllegalStateException.class, () -> service.save(dto));
        verify(writer, never()).write(dto);
    }
}
