package org.sspd.servicemgmt.accountingoptions.periodlock.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.accountingoptions.periodlock.model.AccountingPeriodLock;
import java.time.LocalDate;
import java.util.*;

public interface AccountingPeriodLockRepository extends JpaRepository<AccountingPeriodLock, Integer> {
    @Query("select l from AccountingPeriodLock l where l.active = true and :date between l.dateFrom and l.dateTo")
    List<AccountingPeriodLock> findActiveForDate(@Param("date") LocalDate date);
    List<AccountingPeriodLock> findAllByOrderByDateFromDesc();
}
