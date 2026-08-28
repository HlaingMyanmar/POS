package org.sspd.servicemgmt.securityConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.sspd.servicemgmt.jwt.CustomUserDetailsService;
import org.sspd.servicemgmt.jwt.JwtService;
import org.sspd.servicemgmt.jwt.TokenAwareUserDetails;
import org.sspd.servicemgmt.rbacoptions.permissionoptions.enums.PermissionName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

    private JwtService jwtService;
    private CustomUserDetailsService userDetailsService;
    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        interceptor = new WebSocketAuthInterceptor(jwtService, userDetailsService);
    }

    @Test
    void rejectsConnectWithoutBearerToken() {
        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message(StompCommand.CONNECT, null, null), mockChannel())
        );
    }

    @Test
    void authenticatesValidConnectAndChecksTokenVersion() {
        TokenAwareUserDetails user = user(4, "CAN_ACCESS_SERVICE_JOB_READ");
        when(jwtService.extractUsername("valid-token")).thenReturn("user@example.invalid");
        when(userDetailsService.loadUserByUsername("user@example.invalid")).thenReturn(user);
        when(jwtService.isTokenValid("valid-token", user)).thenReturn(true);
        when(jwtService.extractTokenVersion("valid-token")).thenReturn(4);

        Message<?> result = interceptor.preSend(
                message(StompCommand.CONNECT, "Bearer valid-token", null),
                mockChannel()
        );

        assertNotNull(StompHeaderAccessor.wrap(result).getUser());
    }

    @Test
    void rejectsTechnicianLocationSubscriptionWithoutPermission() {
        UsernamePasswordAuthenticationToken authentication = authentication(
                user(1, "CAN_ACCESS_SERVICE_JOB_READ")
        );

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(
                        message(
                                StompCommand.SUBSCRIBE,
                                authentication,
                                "/topic/technician-location"
                        ),
                        mockChannel()
                )
        );
    }

    @Test
    void allowsTechnicianLocationSubscriptionWithPermission() {
        UsernamePasswordAuthenticationToken authentication = authentication(
                user(1, PermissionName.CAN_ACCESS_TECHNICIAN_LOCATION_READ.name())
        );

        assertDoesNotThrow(() -> interceptor.preSend(
                message(
                        StompCommand.SUBSCRIBE,
                        authentication,
                        "/topic/technician-location"
                ),
                mockChannel()
        ));
    }

    private Message<byte[]> message(
            StompCommand command,
            Object authenticationOrAuthorization,
            String destination
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (authenticationOrAuthorization instanceof String authorization) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (authenticationOrAuthorization instanceof UsernamePasswordAuthenticationToken authentication) {
            accessor.setUser(authentication);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private TokenAwareUserDetails user(int tokenVersion, String... authorities) {
        return new TokenAwareUserDetails(
                "user@example.invalid",
                "encoded-password",
                true,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList(),
                tokenVersion
        );
    }

    private UsernamePasswordAuthenticationToken authentication(TokenAwareUserDetails user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private org.springframework.messaging.MessageChannel mockChannel() {
        return mock(org.springframework.messaging.MessageChannel.class);
    }
}
