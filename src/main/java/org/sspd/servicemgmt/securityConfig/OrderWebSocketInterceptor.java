package org.sspd.servicemgmt.securityConfig;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.sspd.servicemgmt.jwt.*;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;

@Component
@RequiredArgsConstructor
public class OrderWebSocketInterceptor implements ChannelInterceptor {
    private final JwtService jwtService;
    private final CustomUserDetailsService users;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        if (headers.getCommand() == StompCommand.CONNECT) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            if (authorization != null) {
                if (!authorization.startsWith("Bearer ")) throw new AccessDeniedException("Invalid token");
                String token = authorization.substring(7);
                var details = users.loadUserByUsername(jwtService.extractUsername(token));
                if (!details.isEnabled() || !jwtService.isTokenValid(token, details)
                        || !(details instanceof TokenAwareUserDetails aware)
                        || !java.util.Objects.equals(jwtService.extractTokenVersion(token), aware.getTokenVersion())) {
                    throw new AccessDeniedException("Invalid session");
                }
                // Canonical customer principal also supports older phone-based tokens.
                String principal = details instanceof CustomerPortalUserDetails customer
                        ? CustomerPortalAuth.usernameForCustomer(customer.getCustomerId()) : details.getUsername();
                headers.setUser(new UsernamePasswordAuthenticationToken(principal, null, details.getAuthorities()));
            }
        }
        String destination = headers.getDestination();
        if (destination == null) return message;
        if (headers.getCommand() == StompCommand.SEND && !destination.startsWith("/app/")) {
            throw new AccessDeniedException("Clients cannot publish broker notifications");
        }
        if (headers.getCommand() == StompCommand.SUBSCRIBE) {
            Authentication auth = headers.getUser() instanceof Authentication a ? a : null;
            boolean customer = auth != null && CustomerPortalAuth.isCustomerUsername(auth.getName());
            if (destination.contains("*") || destination.contains("{")) throw new AccessDeniedException("Wildcard subscription denied");
            if (customer && !destination.equals("/user/topic/customer-orders")) {
                throw new AccessDeniedException("Customer subscription denied");
            }
            if (destination.equals("/user/topic/customer-orders")) {
                if (!customer) throw new AccessDeniedException("Customer login required");
            } else if (destination.startsWith("/topic/customer-order") || destination.startsWith("/user/")) {
                boolean staff = auth != null && !customer && auth.getAuthorities().stream().anyMatch(a ->
                        a.getAuthority().equals("CAN_ACCESS_SALE_READ") || a.getAuthority().equals("CAN_ACCESS_CUSTOMER_APP_ORDER_READ"));
                if (!destination.equals("/topic/customer-order") || !staff) throw new AccessDeniedException("Order subscription denied");
            }
        }
        return message;
    }
}
