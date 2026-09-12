package org.sspd.servicemgmt.technicianvisitoptions.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.redis.realtime.enabled", havingValue = "true")
public class TechnicianRedisConfig {
    private final TechnicianRealtimeRedis realtime;
    @Bean RedisMessageListenerContainer technicianRedisListener(RedisConnectionFactory factory) {
        var container=new RedisMessageListenerContainer() {
            @Override
            public void start() {
                try {
                    super.start();
                } catch (RuntimeException ex) {
                    org.slf4j.LoggerFactory.getLogger(TechnicianRedisConfig.class)
                            .warn("Redis listener not started (realtime continues over WebSocket): {}", ex.getMessage());
                }
            }
        };
        container.setConnectionFactory(factory);
        container.addMessageListener((message,pattern)->realtime.receive(message.toString()),new PatternTopic("technician.location"));
        return container;
    }
}