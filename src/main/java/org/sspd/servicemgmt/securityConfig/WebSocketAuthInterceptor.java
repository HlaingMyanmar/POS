package org.sspd.servicemgmt.securityConfig;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.jwt.CustomUserDetailsService;
import org.sspd.servicemgmt.jwt.JwtService;
import org.sspd.servicemgmt.jwt.TokenAwareUserDetails;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.enums.PermissionName;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TECHNICIAN_LOCATION_TOPIC = "/topic/technician-location";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
            return message;
        }

        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                && !StompCommand.SEND.equals(accessor.getCommand())) {
            return message;
        }

        Authentication authentication = authenticatedUser(accessor);
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())
                && TECHNICIAN_LOCATION_TOPIC.equals(accessor.getDestination())
                && !hasAuthority(authentication, PermissionName.CAN_ACCESS_TECHNICIAN_LOCATION_READ.name())) {
            throw new MessagingException("Technician location permission is required");
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null) {
            authorization = accessor.getFirstNativeHeader("authorization");
        }
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("WebSocket authentication is required");
        }

        try {
            String jwt = authorization.substring(BEARER_PREFIX.length()).trim();
            if (jwt.isEmpty()) {
                throw new MessagingException("WebSocket authentication is required");
            }

            String username = jwtService.extractUsername(jwt);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!userDetails.isEnabled() || !jwtService.isTokenValid(jwt, userDetails)) {
                throw new MessagingException("Invalid WebSocket access token");
            }

            if (userDetails instanceof TokenAwareUserDetails tokenAware) {
                Integer jwtVersion = jwtService.extractTokenVersion(jwt);
                if (jwtVersion == null || jwtVersion != tokenAware.getTokenVersion()) {
                    throw new MessagingException("WebSocket session has been invalidated");
                }
            }

            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
        } catch (MessagingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MessagingException("Invalid WebSocket access token", ex);
        }
    }

    private Authentication authenticatedUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.isAuthenticated()) {
            return authentication;
        }
        throw new MessagingException("WebSocket authentication is required");
    }

    private boolean hasAuthority(Authentication authentication, String requiredAuthority) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(requiredAuthority::equals);
    }
}
