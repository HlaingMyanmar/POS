package org.sspd.servicemgmt.rbacoptions.roleoptions.enums;

import lombok.Getter;

@Getter
public enum RoleName {

    ADMINISTRATOR("Allow All Permission & System"),
    ADMIN("Allow All Permission"),
    CASHIER("ငွေကိုင် — အရောင်း၊ လက်ခံ၊ Job သတ်မှတ်ခွင့်"),
    TECHNICIAN("ပြုပြင်သူ — မိမိ Job ပြင်ခွင့်၊ တခြားသူ မရွေးရ"),
    PURCHASER("Allow Purchase");


    private final String description;

    RoleName(String description){
        this.description = description;
    }

}
