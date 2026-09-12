package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerPortalResetRequest {
    private String token;
    private String password;
    /** Google ID token for the same email that received the reset link. */
    private String googleIdToken;
}
