package org.sspd.servicemgmt.authoption;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
class AuthRequest {

    @NotBlank
    @Size(max = 100)
    private String usernameOremail;

    @NotBlank
    @Size(max = 200)
    private String password;
}
