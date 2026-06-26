package org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.accountbalanceoptions.model.AccountBalance;

import java.util.Optional;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, Integer> {

    // Account ID နဲ့ Fiscal Year (ဘဏ္ဍာရေးနှစ်) အလိုက် လက်ကျန်ငွေကို ရှာရန်
    Optional<AccountBalance> findByAccountIdAndFiscalYear(Integer accountId, String fiscalYear);

    // Account ID နဲ့တင် ရှာဖွေရန်
    Optional<AccountBalance> findByAccountId(Integer accountId);
}