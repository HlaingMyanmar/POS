package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerAppAccountAdminResult {
    private CustomerAppAccountDTO account;
    /** Present only on create when admin set/generated a password. Show once. */
    private String temporaryPassword;
}
