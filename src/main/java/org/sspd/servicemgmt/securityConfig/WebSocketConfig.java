package org.sspd.servicemgmt.securityConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final OrderWebSocketInterceptor orderInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            OrderWebSocketInterceptor orderInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String allowedOriginsRaw) {
        this.orderInterceptor = orderInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(orderInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        restrictOrigins(registry.addEndpoint("/ws-clinic")).withSockJS();
        restrictOrigins(registry.addEndpoint("/ws-native"));
    }

    private StompWebSocketEndpointRegistration restrictOrigins(StompWebSocketEndpointRegistration registration) {
        if (allowedOrigins.length > 0) {
            registration.setAllowedOrigins(allowedOrigins);
        }
        return registration;
    }
}
