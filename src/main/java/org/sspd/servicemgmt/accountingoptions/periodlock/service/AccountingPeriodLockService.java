package org.sspd.servicemgmt.accountingoptions.periodlock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.periodlock.model.AccountingPeriodLock;
import org.sspd.servicemgmt.accountingoptions.periodlock.repository.AccountingPeriodLockRepository;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class AccountingPeriodLockService {
    private final AccountingPeriodLockRepository repository;
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNTING_PERIOD_LOCK')")
    @Transactional
    public AccountingPeriodLock lock(AccountingPeriodLock request) {
        if (request.getDateFrom() == null || request.getDateTo() == null || request.getDateTo().isBefore(request.getDateFrom()))
            throw new RuntimeException("Valid period start/end dates are required.");
        if (request.getReason() == null || request.getReason().isBlank()) throw new RuntimeException("Lock reason is required.");
        request.setId(null); request.setActive(true); request.setLockedBy(user()); request.setLockedAt(LocalDateTime.now());
        return repository.save(request);
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_ACCOUNTING_PERIOD_LOCK')")
    @Transactional
    public AccountingPeriodLock unlock(Integer id) {
        var lock = repository.findById(id).orElseThrow(() -> new RuntimeException("Period lock not found"));
        lock.setActive(false); lock.setUnlockedBy(user()); lock.setUnlockedAt(LocalDateTime.now()); return repository.save(lock);
    }
    @PreAuthorize("hasAuthority('CAN_ACCESS_JOURNAL_READ')")
    public List<AccountingPeriodLock> list() { return repository.findAllByOrderByDateFromDesc(); }
    private String user() { var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); return a!=null?a.getName():"SYSTEM"; }
}
