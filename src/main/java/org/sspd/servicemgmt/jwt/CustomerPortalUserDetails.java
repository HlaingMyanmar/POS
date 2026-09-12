package org.sspd.servicemgmt.jwt;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class CustomerPortalUserDetails extends TokenAwareUserDetails {

    private final Integer customerId;
    private final String name;
    private final String phone;

    public CustomerPortalUserDetails(String username, String password, boolean enabled,
                                     Collection<? extends GrantedAuthority> authorities,
                                     int tokenVersion, Integer customerId, String name, String phone) {
        super(username, password, enabled, authorities, tokenVersion);
        this.customerId = customerId;
        this.name = name;
        this.phone = phone;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
