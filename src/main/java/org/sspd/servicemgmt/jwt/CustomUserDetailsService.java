package org.sspd.servicemgmt.jwt;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.exceptionhandler.ResourceNotFoundException;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.enums.PermissionName;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrEmail) throws ResourceNotFoundException {


        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username or email: " + usernameOrEmail));

        return new TokenAwareUserDetails(
                user.getEmail(),
                user.getPassword(),
                user.getIsActive(),
                getAuthorities(user),
                user.getTokenVersion() != null ? user.getTokenVersion() : 0
        );




    }
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> "ADMINISTRATOR".equalsIgnoreCase(r.getName()));

        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            role.getPermissions().forEach(p ->
                    authorities.add(new SimpleGrantedAuthority(p.getName())));
        });

        // ADMINISTRATOR ကို permission အားလုံး auto-grant လုပ်တယ်
        if (isAdmin) {
            Arrays.stream(PermissionName.values())
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        }

        return authorities;
    }
}
