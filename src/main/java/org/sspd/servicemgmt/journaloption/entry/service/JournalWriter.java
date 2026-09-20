package org.sspd.servicemgmt.journaloption.entry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.periodlock.service.AccountingPeriodGuard;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository.AccountBalanceRepository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
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
    private final AccountingPeriodGuard periodGuard;

    private static final String ACCOUNTING_TOPIC = "/topic/accounting";

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /**
     * True when a non-reversed original journal exists under the given reference prefix
     * (e.g. {@code SJ-001-SETTLE}). Reversal rows ({@code *-REV} / {@code reversalOf != null}) are ignored.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveReference(String referenceNo) {
        if (referenceNo == null || referenceNo.isBlank()) return false;
        return journalRepository.findByReferenceNo(referenceNo)
                .filter(journal -> journal.getReversalOf() == null)
                .filter(journal -> !"REVERSED".equals(journal.getStatus()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveReferencePrefix(String referencePrefix) {
        if (referencePrefix == null || referencePrefix.isBlank()) return false;
        return journalRepository.findAllByReferenceNoStartingWith(referencePrefix).stream()
                .anyMatch(journal -> journal.getReversalOf() == null
                        && !"REVERSED".equals(journal.getStatus()));
    }

    @Transactional
    public JournalEntryDTO write(JournalEntryDTO dto) {
        validate(dto);
        periodGuard.assertOpen(dto.getEntryDate(), "post journal");
        if (dto.getReferenceNo() != null && dto.getReferenceNo().isBlank()) {
            dto.setReferenceNo(null);
        }
        if (dto.getReferenceNo() != null && journalRepository.existsByReferenceNo(dto.getReferenceNo())) {
            throw new IllegalStateException("Journal already posted for reference: " + dto.getReferenceNo());
        }

        BigDecimal totalDebit  = dto.getDetails().stream()
                .map(d -> d.getDebit()  != null ? d.getDebit()  : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = dto.getDetails().stream()
                .map(d -> d.getCredit() != null ? d.getCredit() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0 || totalCredit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Journal debit and credit totals must be greater than zero.");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new RuntimeException(
                "Accounting Error: Debit (" + totalDebit + ") and Credit (" + totalCredit + ") must be equal!");
        }

        lockAccounts(dto.getDetails().stream().map(JournalDetailDTO::getAccountId).toList());
        assertAssetCreditsCovered(dto);
        JournalEntry journal = journalMapper.toEntity(dto);
        if (journal.getStatus() == null || journal.getStatus().isBlank()) journal.setStatus("POSTED");
        if (dto.getStaffId() != null) {
            journal.setStaff(staffRepository.findById(dto.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff not found")));
        }
        JournalEntry saved;
        try {
            saved = journalRepository.save(journal);
            entityManager.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("Journal already posted for reference: " + dto.getReferenceNo(), ex);
        }

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

            updateAccountBalance(account, detailDto.getDebit(), detailDto.getCredit(), saved.getEntryDate());
        }

        messagingTemplate.convertAndSend(ACCOUNTING_TOPIC, "JOURNAL_CREATED");
        return journalMapper.toDto(saved);
    }

    @Transactional
    public void reverseByReferenceNo(String referenceNo) {
        reverseByReferenceNo(referenceNo, "system", "Reversal");
    }

    @Transactional
    public void reverseByReferenceNo(String referenceNo, String actor, String reason) {
        journalRepository.findByReferenceNo(referenceNo).ifPresent(journal -> {
            if ("REVERSED".equals(journal.getStatus()) || journalRepository.findByReferenceNo(referenceNo + "-REV").isPresent()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            periodGuard.assertOpen(journal.getEntryDate(), "reverse journal " + referenceNo);
            periodGuard.assertOpen(now, "post journal reversal " + referenceNo);
            lockAccounts(journal.getDetails().stream().map(detail -> detail.getAccount().getId()).toList());
            JournalEntry reversal = JournalEntry.builder()
                    .entryDate(now)
                    .referenceNo(referenceNo + "-REV")
                    .description("Reversal of " + referenceNo + ": " + reason)
                    .staff(journal.getStaff())
                    .status("POSTED")
                    .reversalOf(journal)
                    .reversedBy(actor)
                    .reversedAt(now)
                    .reversalReason(reason)
                    .build();
            reversal = journalRepository.save(reversal);
            entityManager.flush();
            for (JournalDetail detail : journal.getDetails()) {
                BigDecimal reversedDebit = detail.getCredit() != null ? detail.getCredit() : BigDecimal.ZERO;
                BigDecimal reversedCredit = detail.getDebit() != null ? detail.getDebit() : BigDecimal.ZERO;
                detailRepository.save(JournalDetail.builder()
                        .journalEntry(reversal)
                        .account(detail.getAccount())
                        .debit(reversedDebit)
                        .credit(reversedCredit)
                        .build());
                updateAccountBalance(detail.getAccount(), reversedDebit, reversedCredit, reversal.getEntryDate());
            }
            journal.setStatus("REVERSED");
            journal.setReversedBy(actor);
            journal.setReversedAt(now);
            journal.setReversalReason(reason);
            journalRepository.save(journal);
            messagingTemplate.convertAndSend(ACCOUNTING_TOPIC, "JOURNAL_REVERSED");
        });
    }

    @Transactional
    public void reverseByReferencePrefix(String referencePrefix, String actor, String reason) {
        journalRepository.findAllByReferenceNoStartingWith(referencePrefix).stream()
                .filter(journal -> journal.getReversalOf() == null)
                .map(JournalEntry::getReferenceNo)
                .toList()
                .forEach(reference -> reverseByReferenceNo(reference, actor, reason));
    }

    private void validate(JournalEntryDTO dto) {
        if (dto.getDetails() == null || dto.getDetails().isEmpty()) {
            throw new IllegalArgumentException("Journal details are required");
        }
        for (JournalDetailDTO detail : dto.getDetails()) {
            if (detail.getAccountId() == null) {
                throw new IllegalArgumentException("Account is required on every journal line");
            }
            BigDecimal debit = detail.getDebit() != null ? detail.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = detail.getCredit() != null ? detail.getCredit() : BigDecimal.ZERO;
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new IllegalArgumentException("Journal debit and credit cannot be negative");
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw new IllegalArgumentException("A journal line cannot have both debit and credit");
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new IllegalArgumentException("A journal line must have a debit or a credit");
            }
        }
    }

    private void updateAccountBalance(ChartOfAccount account, BigDecimal debit, BigDecimal credit,
                                      LocalDateTime entryDate) {
        LocalDateTime effectiveDate = entryDate != null ? entryDate : LocalDateTime.now();
        String year = String.valueOf(effectiveDate.getYear());
        // Persist preceding lines before refreshing a previously managed balance.
        entityManager.flush();
        AccountBalance balance = balanceRepository.findForUpdate(account.getId(), year).orElse(null);
        if (balance == null) {
            balance = new AccountBalance(null, account, year, BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.now());
        } else {
            entityManager.refresh(balance, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }

        String accountType = String.valueOf(account.getAccountType());
        boolean debitNormal = "Asset".equals(accountType) || "Expense".equals(accountType);
        BigDecimal safeDebit = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal safeCredit = credit == null ? BigDecimal.ZERO : credit;
        BigDecimal netChange = debitNormal ? safeDebit.subtract(safeCredit) : safeCredit.subtract(safeDebit);

        balance.setCurrentBalance(balance.getCurrentBalance().add(netChange));
        balance.setLastUpdated(LocalDateTime.now());
        balanceRepository.save(balance);
    }

    private void lockAccounts(java.util.List<Integer> accountIds) {
        // Parent locks also serialize creation of missing fiscal-year balances.
        accountIds.stream().distinct().sorted().forEach(id ->
                coaRepository.findByIdForUpdate(id).orElseThrow(() ->
                        new ResourceNotFoundException("Account not found with ID: " + id)));
    }

    /**
     * After COA locks are held, re-check asset accounts being credited so concurrent
     * outgoing payments cannot both pass on a stale snapshot and drive cash/bank negative.
     */
    private void assertAssetCreditsCovered(JournalEntryDTO dto) {
        java.util.Map<Integer, BigDecimal> netCreditByAccount = new java.util.TreeMap<>();
        for (JournalDetailDTO detail : dto.getDetails()) {
            BigDecimal debit = detail.getDebit() != null ? detail.getDebit() : BigDecimal.ZERO;
            BigDecimal credit = detail.getCredit() != null ? detail.getCredit() : BigDecimal.ZERO;
            netCreditByAccount.merge(detail.getAccountId(), credit.subtract(debit), BigDecimal::add);
        }
        for (var entry : netCreditByAccount.entrySet()) {
            if (entry.getValue().signum() <= 0) continue;
            ChartOfAccount account = coaRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + entry.getKey()));
            if (account.getAccountType() != AccountType.Asset) continue;
            BigDecimal available = detailRepository.netDebitByAccountId(entry.getKey());
            if (available == null) available = BigDecimal.ZERO;
            if (available.compareTo(entry.getValue()) < 0) {
                String name = account.getAccountName() != null ? account.getAccountName() : ("Account " + account.getId());
                throw new RuntimeException(
                        name + " တွင် လက်ကျန်မလောက်ပါ။ " +
                        "ကျန်ငွေ: " + available.toPlainString() + " Ks၊ လွှဲမည့်ပမာဏ: " + entry.getValue().toPlainString() + " Ks");
            }
        }
    }

}
