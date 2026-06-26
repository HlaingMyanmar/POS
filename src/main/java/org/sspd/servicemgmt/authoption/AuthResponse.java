package org.sspd.servicemgmt.authoption;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
class AuthResponse {
    private String accessToken;
    private String username;
    private String name;
    private String phone;
    private Set<String> roles;
    private Set<String> permissions;
}
