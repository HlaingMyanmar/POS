package org.sspd.servicemgmt.accountingoptions.periodlock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.accountingoptions.periodlock.repository.AccountingPeriodLockRepository;
import java.time.*;

@Component @RequiredArgsConstructor
public class AccountingPeriodGuard {
    private final AccountingPeriodLockRepository repository;
    public void assertOpen(LocalDate date, String operation) {
        if (date == null) date = LocalDate.now();
        var locks = repository.findActiveForDate(date);
        if (!locks.isEmpty()) {
            var lock = locks.get(0);
            throw new IllegalStateException("Accounting period is locked (" + lock.getDateFrom() + " to "
                    + lock.getDateTo() + "). Cannot " + operation + ". Reason: " + lock.getReason());
        }
    }
    public void assertOpen(LocalDateTime date, String operation) {
        assertOpen(date != null ? date.toLocalDate() : LocalDate.now(), operation);
    }
}
