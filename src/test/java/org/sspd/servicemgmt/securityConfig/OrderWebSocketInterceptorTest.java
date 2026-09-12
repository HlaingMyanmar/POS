package org.sspd.servicemgmt.securityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.sspd.servicemgmt.jwt.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderWebSocketInterceptorTest {
    private final JwtService jwt = mock(JwtService.class);
    private final CustomUserDetailsService users = mock(CustomUserDetailsService.class);
    private final OrderWebSocketInterceptor interceptor = new OrderWebSocketInterceptor(jwt, users);

    private void subscribe(String destination, String username, String authority) {
        var h = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        h.setDestination(destination);
        if (username != null) h.setUser(new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority(authority))));
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null);
    }

    @Test void customerCanOnlyReadTheirUserDestination() {
        assertDoesNotThrow(() -> subscribe("/user/topic/customer-orders", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-order", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-orders-userOther", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/user/customer:id:8/topic/customer-orders", "customer:id:7", "ROLE_CUSTOMER"));
    }

    @Test void anonymousAndWildcardSubscriptionsCannotReadOrders() {
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-order", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/user/topic/customer-orders", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/**", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-orders-userOther", "staff", "CAN_ACCESS_SALE_READ"));
        assertDoesNotThrow(() -> subscribe("/topic/customer-order", "staff", "CAN_ACCESS_SALE_READ"));
    }

    @Test void clientsCannotForgeNotifications() {
        var h = StompHeaderAccessor.create(StompCommand.SEND);
        h.setDestination("/topic/customer-order");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
    }

    @Test void connectCanonicalizesCustomerAndRejectsRevokedTokens() {
        var details = new CustomerPortalUserDetails("customer:09123", "", true,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), 2, 7, "Customer", "09123");
        when(jwt.extractUsername("token")).thenReturn("customer:09123");
        when(users.loadUserByUsername("customer:09123")).thenReturn(details);
        when(jwt.isTokenValid("token", details)).thenReturn(true);
        when(jwt.extractTokenVersion("token")).thenReturn(2);
        var h = StompHeaderAccessor.create(StompCommand.CONNECT);
        h.setNativeHeader("Authorization", "Bearer token");
        h.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null);
        assertEquals("customer:id:7", h.getUser().getName());
        when(jwt.extractTokenVersion("token")).thenReturn(1);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
    }
}
