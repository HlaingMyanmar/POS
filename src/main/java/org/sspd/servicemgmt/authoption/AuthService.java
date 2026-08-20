package org.sspd.servicemgmt.authoption;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.jwt.CustomUserDetailsService;
import org.sspd.servicemgmt.jwt.JwtService;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Transactional
    public LoginResult authenticateUser(AuthRequest request) {

        // ၁. Login စစ်ဆေးခြင်း
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOremail(),
                        request.getPassword()
                )
        );

        // ၂. Token version increment — ဒီအတွက် ရှေ့က session တွေ invalid ဖြစ်သွားမယ်
        User user = userRepository.findByUsernameOrEmail(
                        request.getUsernameOremail(), request.getUsernameOremail())
                .orElseThrow(() -> new BadCredentialsException("Username သို့မဟုတ် Password မှားနေပါသည်"));
        int newVersion = (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
        user.setTokenVersion(newVersion);
        userRepository.save(user);

        // ၃. User အချက်အလက်ယူခြင်း
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsernameOremail());

        // ၄. Access Token နှင့် Refresh Token ထုတ်ခြင်း (version ပါ ထည့်)
        String accessToken = jwtService.generateToken(userDetails, newVersion);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        // ၅. Roles နဲ့ Permissions ခွဲထုတ်ခြင်း
        Set<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        Set<String> permissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        // ၆. ရလာသမျှ အချက်အလက်အားလုံးကို စုစည်းပြီး ပြန်ပေးခြင်း
        return new LoginResult(accessToken, refreshToken, userDetails.getUsername(), user.getName(), user.getPhone(),
                user.getStaff() != null ? user.getStaff().getId() : null, roles, permissions);
    }

    // Login ရလဒ်များကို သယ်ဆောင်ရန် အတွင်းသုံး record (သို့မဟုတ် DTO)
        public record LoginResult(String accessToken, String refreshToken, String username, String name, String phone,
                                                          Integer staffId, Set<String> roles, Set<String> permissions) {}
}