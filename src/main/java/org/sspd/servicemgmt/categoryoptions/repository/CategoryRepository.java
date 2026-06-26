package org.sspd.servicemgmt.categoryoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.categoryoptions.model.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // ၁။ နာမည်နဲ့ ရှာဖို့ (Unique ဖြစ်လို့ Optional နဲ့ သုံးတာ ကောင်းပါတယ်)
    Optional<Category> findByName(String name);

    // ၂။ နာမည် ရှိမရှိ စစ်ဆေးဖို့
    boolean existsByName(String name);


    // ၃။ မိခင် Category (Parent) မရှိတဲ့ အဆင့်မြင့်ဆုံး Category တွေကိုပဲ ရှာဖို့
    // ဥပမာ - Root Categories တွေကိုပဲ list ထုတ်ချင်ရင် သုံးပါတယ်
    List<Category> findAllByParentIsNull();

    // ၄။ သတ်မှတ်ထားတဲ့ Parent တစ်ခုရဲ့ လက်အောက်ခံ Category (Sub-categories) တွေကို ရှာဖို့
    List<Category> findAllByParentId(Long parentId);

    // ၅။ Active ဖြစ်နေတဲ့ Category တွေကိုပဲ ရှာဖို့
    List<Category> findAllByIsActiveTrue();

    // ၆။ Parent မရှိတဲ့အပြင် Active လည်းဖြစ်တဲ့ Category တွေကို ရှာဖို့
    List<Category> findAllByParentIsNullAndIsActiveTrue();

    List<Category> findAllByIsActive(boolean status);
}
