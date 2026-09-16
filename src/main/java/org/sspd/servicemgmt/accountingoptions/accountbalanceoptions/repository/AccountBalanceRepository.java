package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;

import java.util.Optional;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from AccountBalance b where b.account.id = :accountId and b.fiscalYear = :year")
    Optional<AccountBalance> findForUpdate(
            @org.springframework.data.repository.query.Param("accountId") Integer accountId,
            @org.springframework.data.repository.query.Param("year") String year);

    // Account ID နဲ့ Fiscal Year (ဘဏ္ဍာရေးနှစ်) အလိုက် လက်ကျန်ငွေကို ရှာရန်
    Optional<AccountBalance> findByAccountIdAndFiscalYear(Integer accountId, String fiscalYear);

    // Account ID နဲ့တင် ရှာဖွေရန်
    Optional<AccountBalance> findByAccountId(Integer accountId);
}
