package org.sspd.servicemgmt.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final JwtService.ParsedToken parsed;

        // 1. Authorization Header ပါမပါ နဲ့ "Bearer " နဲ့ စမစ စစ်ဆေးခြင်း
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Header ထဲကနေ Token ကို ခွဲထုတ်ယူခြင်း (Bearer ဆိုတဲ့စာသားနောက်က အပိုင်း)
        jwt = authHeader.substring(7);

        // Parse and validate signature/expiration exactly once. Any malformed,
        // expired, unsupported, or incorrectly signed token becomes a stable 401.
        try {
            parsed = jwtService.parseToken(jwt);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, "TOKEN_EXPIRED_OR_INVALID",
                    "Access token expired or invalid.");
            return;
        }
        if (!parsed.isAccessToken() || parsed.username() == null || parsed.username().isBlank()) {
            writeUnauthorized(response, "TOKEN_EXPIRED_OR_INVALID",
                    "Access token expired or invalid.");
            return;
        }

        // 4. Email ရှိပြီး လက်ရှိ SecurityContext မှာ Authentication မရှိသေးရင်
        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails;
            try {
                userDetails = this.userDetailsService.loadUserByUsername(parsed.username());
            } catch (RuntimeException ignored) {
                writeUnauthorized(response, "TOKEN_EXPIRED_OR_INVALID",
                        "Access token expired or invalid.");
                return;
            }

            // 5. Token version စစ်ဆေးခြင်း — version မတိုက်မိရင် session invalid ဖြစ်သွားပြီ
            if (userDetails instanceof TokenAwareUserDetails tud) {
                Integer jwtVersion = parsed.tokenVersion();
                if (jwtVersion == null || jwtVersion != tud.getTokenVersion()) {
                    writeUnauthorized(response, "SESSION_INVALIDATED",
                            "This session has been invalidated. Please log in again.");
                    return;
                }
            }

            // 6. Token မှန်ကန်မှု စစ်ဆေးခြင်း
            if (!userDetails.isEnabled() || !parsed.username().equals(userDetails.getUsername())) {
                writeUnauthorized(response, "TOKEN_EXPIRED_OR_INVALID",
                        "Access token expired or invalid.");
                return;
            }
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        // နောက် filter တစ်ခုကို ဆက်သွားစေခြင်း
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletResponse response,
            String error,
            String message
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}"
        );
    }
}