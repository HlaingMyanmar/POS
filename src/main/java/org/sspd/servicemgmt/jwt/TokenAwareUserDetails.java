package org.sspd.servicemgmt.jwt;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class TokenAwareUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    private final int tokenVersion;

    public TokenAwareUserDetails(String username, String password, boolean enabled,
                                  Collection<? extends GrantedAuthority> authorities,
                                  int tokenVersion) {
        this.username     = username;
        this.password     = password;
        this.enabled      = enabled;
        this.authorities  = authorities;
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() { return tokenVersion; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword()   { return password; }
    @Override public String getUsername()   { return username; }
    @Override public boolean isEnabled()    { return enabled; }
    @Override public boolean isAccountNonExpired()   { return true; }
    @Override public boolean isAccountNonLocked()    { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
