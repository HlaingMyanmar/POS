package org.sspd.servicemgmt.authoption;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
class AuthRequest {
    private String usernameOremail;
    private String password;

}
