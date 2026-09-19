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

import java.util.Set;

@Component
@RequiredArgsConstructor
public class OrderWebSocketInterceptor implements ChannelInterceptor {
    private static final Set<String> CUSTOMER_SUBSCRIBE = Set.of(
            "/user/topic/customer-orders",
            "/user/topic/chat"
    );
    private static final Set<String> CUSTOMER_SEND = Set.of(
            "/app/chat.send",
            "/app/chat/send"
    );
    private static final Set<String> STAFF_ORDER_SUBSCRIBE = Set.of("/topic/customer-order");

    private final JwtService jwtService;
    private final CustomUserDetailsService users;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        StompCommand command = headers.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticateConnect(headers);
            return message;
        }
        Authentication auth = headers.getUser() instanceof Authentication a ? a : null;
        String destination = headers.getDestination();
        if (command == StompCommand.SEND) {
            authorizeSend(destination, auth);
        } else if (command == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(destination, auth);
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor headers) {
        String authorization = headers.getFirstNativeHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new AccessDeniedException("Login required");
        }
        if (!authorization.startsWith("Bearer ")) throw new AccessDeniedException("Invalid token");
        String token = authorization.substring(7);
        try {
            JwtService.ParsedToken parsed = jwtService.parseToken(token);
            if (!parsed.isAccessToken() || parsed.username() == null || parsed.username().isBlank()) {
                throw new AccessDeniedException("Invalid token");
            }
            var details = users.loadUserByUsername(parsed.username());
            if (!details.isEnabled() || !parsed.username().equals(details.getUsername())
                    || !(details instanceof TokenAwareUserDetails aware)
                    || parsed.tokenVersion() == null
                    || parsed.tokenVersion() != aware.getTokenVersion()) {
                throw new AccessDeniedException("Invalid session");
            }
            String principal = details instanceof CustomerPortalUserDetails customer
                    ? CustomerPortalAuth.usernameForCustomer(customer.getCustomerId()) : details.getUsername();
            headers.setUser(new UsernamePasswordAuthenticationToken(
                    principal, null, details.getAuthorities()));
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AccessDeniedException("Invalid token");
        }
    }

    private void authorizeSend(String destination, Authentication auth) {
        if (destination == null || !destination.startsWith("/app/")) {
            throw new AccessDeniedException("Clients cannot publish broker notifications");
        }
        Authentication principal = requireUser(auth);
        if (isCustomer(principal) && !CUSTOMER_SEND.contains(destination)) {
            throw new AccessDeniedException("Customer send denied");
        }
    }

    private void authorizeSubscribe(String destination, Authentication auth) {
        if (destination == null
                || destination.contains("*")
                || destination.contains("{")
                || destination.contains("}")
                || destination.contains("..")
                || destination.contains("%")) {
            throw new AccessDeniedException("Wildcard subscription denied");
        }
        Authentication principal = requireUser(auth);
        if (isCustomer(principal)) {
            if (!CUSTOMER_SUBSCRIBE.contains(destination)) {
                throw new AccessDeniedException("Customer subscription denied");
            }
            return;
        }
        if (destination.startsWith("/topic/customer-order")) {
            if (!STAFF_ORDER_SUBSCRIBE.contains(destination) || !hasStaffOrderAccess(principal)) {
                throw new AccessDeniedException("Order subscription denied");
            }
            return;
        }
        if (destination.startsWith("/topic/") && destination.indexOf('/', 7) < 0) {
            return;
        }
        throw new AccessDeniedException("Subscription denied");
    }

    private static Authentication requireUser(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new AccessDeniedException("Login required");
        }
        return auth;
    }

    private static boolean isCustomer(Authentication auth) {
        return CustomerPortalAuth.isCustomerUsername(auth.getName());
    }

    private static boolean hasStaffOrderAccess(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("CAN_ACCESS_SALE_READ")
                        || a.getAuthority().equals("CAN_ACCESS_CUSTOMER_APP_ORDER_READ"));
    }
}
