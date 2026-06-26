package org.sspd.servicemgmt.rbacoptions.roleoptions.enums;

import lombok.Getter;

@Getter
public enum RoleName {

    ADMINISTRATOR("Allow All Permission & System"),
    ADMIN("Allow All Permission"),
    CASHIER("Allow Sale"),
    PURCHASER("Allow Purchase");


    private final String description;

    RoleName(String description){
        this.description = description;
    }

}
