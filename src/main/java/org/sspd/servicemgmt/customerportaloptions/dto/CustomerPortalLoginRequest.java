package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerPortalLoginRequest {
    /** Phone, email, or customer name. Legacy clients may still send this as {@link #phone}. */
    private String login;
    /** Legacy field — used when {@link #login} is blank. */
    private String phone;
    private String password;

    public String resolveLoginId() {
        if (login != null && !login.isBlank()) return login.trim();
        if (phone != null && !phone.isBlank()) return phone.trim();
        return "";
    }
}
