package org.sspd.servicemgmt.authoption;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String username;
    private String name;
    private String phone;
    private Integer staffId;
    private Set<String> roles;
    private Set<String> permissions;
}
