package org.sspd.servicemgmt.unitsoptions.enums;

import lombok.Getter;

@Getter
public enum UnitName {

    // အရေအတွက် (Quantity/Packaging)
    PCS("pcs"),        // constant ကို စာလုံးအကြီးထားပြီး description မှာ pcs လို့ပေးပါ
    BOX("box"),
    PACK("pack"),
    SET("set"),
    DOZEN("dozen"),
    BOTTLE("bottle"),
    BAG("bag"),

    // အလေးချိန် (Weight)
    KG("kg"),
    G("g"),
    LB("lb"),
    VISS("viss"),
    TICAL("tical"),

    // အရည် (Volume)
    L("liter"),
    ML("ml"),
    GAL("gallon"),

    // အလျား (Length)
    M("meter"),
    FT("feet"),
    YD("yard"),
    IN("inch");

    private final String description;

    UnitName(String description) {
        this.description = description;
    }
}