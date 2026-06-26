package org.sspd.servicemgmt.rbacoptions.useroptions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class UserDTO {
    private Long id;
    private String username;
    @NotNull(message = "Require Email")
    @Email
    private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String authProvider;
    private Boolean isActive;
    private String name;
    private String phone;
    private Set<String> roles;


}