package org.sspd.servicemgmt.accountingoptions.coaoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.accountingoptions.coaoptions.model.ChartOfAccount;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Integer> {

    // ၁။ အကောင့်ကုဒ်ဖြင့် ရှာဖွေရန်
    Optional<ChartOfAccount> findByCode(String code);

    // ၂။ အပေါ်ဆုံးအဆင့် (Root) အကောင့်များကိုသာ ရှာဖွေရန် (Tree View အတွက် အရမ်းအရေးကြီးသည်)
    List<ChartOfAccount> findAllByParentIsNull();

    // ၃။ အကောင့်အမျိုးအစားအလိုက် ရှာဖွေရန် (Asset, Liability, စသည်ဖြင့်)
    List<ChartOfAccount> findByAccountType(AccountType accountType);

    // ၄။ အကောင့်အမျိုးအစားအလိုက် အရေအတွက်ကို ရေတွက်ရန် (Auto Code Generate လုပ်ရာတွင် သုံးသည်)
    long countByAccountType(AccountType accountType);

    // ၅။ အကောင့်နာမည်ဖြင့် ရှာဖွေရန် (Search Function အတွက်)
    List<ChartOfAccount> findByAccountNameContainingIgnoreCase(String accountName);

    // ၆။ ကုဒ်နံပါတ် ရှိမရှိ စစ်ဆေးရန်
    boolean existsByCode(String code);

    boolean existsByAccountName(String name);
}