package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerAppAccountLinkRequest {
    private Integer customerId;
    private String email;
    private String phone;
    private String password;
    /** When true (or password blank), a one-time password is generated and returned. */
    private Boolean generatePassword;
}
