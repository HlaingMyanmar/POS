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

    @Test void customerCanReadOrdersAndChat() {
        assertDoesNotThrow(() -> subscribe("/user/topic/customer-orders", "customer:id:7", "ROLE_CUSTOMER"));
        assertDoesNotThrow(() -> subscribe("/user/topic/chat", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/chat", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-order", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/technician-location", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-orders-userOther", "customer:id:7", "ROLE_CUSTOMER"));
        assertThrows(AccessDeniedException.class, () -> subscribe("/user/customer:id:8/topic/customer-orders", "customer:id:7", "ROLE_CUSTOMER"));
    }

    @Test void anonymousCannotSubscribeSensitiveTopics() {
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/chat", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/technician-location", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/handovers", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/data-events", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/accounting", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-order", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/user/topic/customer-orders", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/**", null, ""));
        assertThrows(AccessDeniedException.class, () -> subscribe("/topic/customer-orders-userOther", "staff", "CAN_ACCESS_SALE_READ"));
        assertDoesNotThrow(() -> subscribe("/topic/customer-order", "staff", "CAN_ACCESS_SALE_READ"));
        assertDoesNotThrow(() -> subscribe("/topic/chat", "staff", "CAN_ACCESS_SALE_READ"));
        assertDoesNotThrow(() -> subscribe("/topic/technician-location", "staff", "CAN_ACCESS_SALE_READ"));
    }

    @Test void clientsCannotForgeNotifications() {
        var h = StompHeaderAccessor.create(StompCommand.SEND);
        h.setDestination("/topic/customer-order");
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
    }

    @Test void customerCannotSendStaffDestinations() {
        var handover = StompHeaderAccessor.create(StompCommand.SEND);
        handover.setDestination("/app/handover.accept");
        handover.setUser(new UsernamePasswordAuthenticationToken("customer:id:7", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], handover.getMessageHeaders()), null));
        var chat = StompHeaderAccessor.create(StompCommand.SEND);
        chat.setDestination("/app/chat/send");
        chat.setUser(new UsernamePasswordAuthenticationToken("customer:id:7", null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        assertDoesNotThrow(() -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], chat.getMessageHeaders()), null));
    }

    @Test void connectRequiresBearerToken() {
        var h = StompHeaderAccessor.create(StompCommand.CONNECT);
        h.setLeaveMutable(true);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
    }

    @Test void connectCanonicalizesCustomerAndRejectsRevokedTokens() {
        var details = new CustomerPortalUserDetails("customer:09123", "", true,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), 2, 7, "Customer", "09123");
        when(jwt.parseToken("token")).thenReturn(
                new JwtService.ParsedToken("customer:09123", 2, "access"));
        when(users.loadUserByUsername("customer:09123")).thenReturn(details);
        var h = StompHeaderAccessor.create(StompCommand.CONNECT);
        h.setNativeHeader("Authorization", "Bearer token");
        h.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null);
        assertEquals("customer:id:7", h.getUser().getName());
        when(jwt.parseToken("token")).thenReturn(
                new JwtService.ParsedToken("customer:09123", 1, "access"));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
    }

    @Test void malformedWebSocketTokenIsControlledAccessDenied() {
        when(jwt.parseToken("malformed"))
                .thenThrow(new io.jsonwebtoken.MalformedJwtException("bad token"));
        var h = StompHeaderAccessor.create(StompCommand.CONNECT);
        h.setNativeHeader("Authorization", "Bearer malformed");
        h.setLeaveMutable(true);

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null));
        verifyNoInteractions(users);
    }
}
