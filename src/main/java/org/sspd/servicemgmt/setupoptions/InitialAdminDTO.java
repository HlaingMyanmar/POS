package org.sspd.servicemgmt.setupoptions;

import lombok.Data;

@Data
public class InitialAdminDTO {
    private String username;
    private String email;
    private String password;
}
