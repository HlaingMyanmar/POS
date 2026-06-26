package org.sspd.servicemgmt.journaloption.entry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.journaloption.entry.dto.JournalEntryDTO;
import org.sspd.servicemgmt.journaloption.entry.mapper.JournalMapper;
import org.sspd.servicemgmt.journaloption.entry.repository.JournalEntryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JournalEntryService {

    private final JournalEntryRepository journalRepository;
    private final JournalMapper journalMapper;
    private final JournalWriter journalWriter;

    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_CREATE')")
    @Transactional
    public JournalEntryDTO save(JournalEntryDTO dto) {
        return journalWriter.write(dto);
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_READ')")
    @Transactional(readOnly = true)
    public List<JournalEntryDTO> findAll() {
        return journalRepository.findAll().stream()
                .map(journalMapper::toDto)
                .toList();
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_READ')")
    @Transactional(readOnly = true)
    public JournalEntryDTO findById(int id) {
        return journalMapper.toDto(
            journalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal Entry Not Found with id " + id))
        );
    }
}
