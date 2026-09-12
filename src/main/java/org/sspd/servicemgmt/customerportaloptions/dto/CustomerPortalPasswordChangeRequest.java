package org.sspd.servicemgmt.customerportaloptions.dto;

import lombok.Data;

@Data
public class CustomerPortalPasswordChangeRequest {
    private String currentPassword;
    private String newPassword;
}
