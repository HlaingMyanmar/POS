package org.sspd.servicemgmt.journaloption.entry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository.AccountBalanceRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;
import org.sspd.servicemgmt.accountingoptions.coaoptions.repository.ChartOfAccountRepository;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
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

/**
 * Internal journal persistence — no security gate so system services
 * (SaleService, PurchaseService, etc.) can create journals as side effects
 * without requiring CAN_ACCESS_JOURNAL_CREATE on the calling user.
 *
 * JournalEntryService delegates here and adds @PreAuthorize for the UI endpoint.
 */
@Service
@RequiredArgsConstructor
public class JournalWriter {

    private final JournalEntryRepository journalRepository;
    private final JournalDetailRepository detailRepository;
    private final AccountBalanceRepository balanceRepository;
    private final ChartOfAccountRepository coaRepository;
    private final StaffRepository staffRepository;
    private final JournalMapper journalMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String ACCOUNTING_TOPIC = "/topic/accounting";

    @Transactional
    public JournalEntryDTO write(JournalEntryDTO dto) {
        BigDecimal totalDebit  = dto.getDetails().stream()
                .map(d -> d.getDebit()  != null ? d.getDebit()  : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = dto.getDetails().stream()
                .map(d -> d.getCredit() != null ? d.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new RuntimeException(
                "Accounting Error: Debit (" + totalDebit + ") and Credit (" + totalCredit + ") must be equal!");
        }

        JournalEntry journal = journalMapper.toEntity(dto);
        if (dto.getStaffId() != null) {
            journal.setStaff(staffRepository.findById(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found")));
        }
        JournalEntry saved = journalRepository.save(journal);

        for (JournalDetailDTO detailDto : dto.getDetails()) {
            ChartOfAccount account = coaRepository.findById(detailDto.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found with ID: " + detailDto.getAccountId()));

            detailRepository.save(JournalDetail.builder()
                    .journalEntry(saved)
                    .account(account)
                    .debit(detailDto.getDebit())
                    .credit(detailDto.getCredit())
                    .build());

            updateAccountBalance(account, detailDto.getDebit(), detailDto.getCredit());
        }

        messagingTemplate.convertAndSend(ACCOUNTING_TOPIC, "JOURNAL_CREATED");
        return journalMapper.toDto(saved);
    }

    @Transactional
    public void reverseByReferenceNo(String referenceNo) {
        journalRepository.findByReferenceNo(referenceNo).ifPresent(journal -> {
            for (JournalDetail detail : journal.getDetails()) {
                reverseAccountBalance(detail.getAccount(), detail.getDebit(), detail.getCredit());
            }
            journalRepository.delete(journal);
        });
    }

    private void updateAccountBalance(ChartOfAccount account, BigDecimal debit, BigDecimal credit) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        AccountBalance balance = balanceRepository
                .findByAccountIdAndFiscalYear(account.getId(), year)
                .orElse(new AccountBalance(null, account, year, BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.now()));

        BigDecimal netChange = switch (account.getAccountType()) {
            case Asset, Expense             -> debit.subtract(credit);
            case Liability, Equity, Income  -> credit.subtract(debit);
        };

        balance.setCurrentBalance(balance.getCurrentBalance().add(netChange));
        balance.setLastUpdated(LocalDateTime.now());
        balanceRepository.save(balance);
    }

    private void reverseAccountBalance(ChartOfAccount account, BigDecimal debit, BigDecimal credit) {
        String year = String.valueOf(LocalDateTime.now().getYear());
        balanceRepository.findByAccountIdAndFiscalYear(account.getId(), year).ifPresent(balance -> {
            BigDecimal netChange = switch (account.getAccountType()) {
                case Asset, Expense            -> credit.subtract(debit);
                case Liability, Equity, Income -> debit.subtract(credit);
            };
            balance.setCurrentBalance(balance.getCurrentBalance().add(netChange));
            balance.setLastUpdated(LocalDateTime.now());
            balanceRepository.save(balance);
        });
    }
}
