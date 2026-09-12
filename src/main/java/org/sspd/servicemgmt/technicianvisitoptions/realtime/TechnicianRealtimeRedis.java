package org.sspd.servicemgmt.technicianvisitoptions.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.technicianvisitoptions.dto.TechnicianVisitDTO;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.redis.realtime.enabled", havingValue = "true")
public class TechnicianRealtimeRedis {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final SimpMessagingTemplate websocket;
    @Value("${app.redis.realtime.enabled:true}") private boolean enabled;

    public void publish(TechnicianVisitDTO dto) {
        if (!enabled || dto == null || dto.staffId() == null) { websocket.convertAndSend("/topic/technician-location", dto); return; }
        try {
            String payload=json.writeValueAsString(dto);
            String base="technician:"+dto.staffId();
            redis.opsForValue().set(base+":presence","online",Duration.ofSeconds(90));
            redis.opsForValue().set(base+":location",payload,Duration.ofMinutes(5));
            redis.convertAndSend("technician.location",payload);
        } catch(Exception ex) { websocket.convertAndSend("/topic/technician-location",dto); }
    }

    public void receive(String payload) {
        try { websocket.convertAndSend("/topic/technician-location",json.readValue(payload,TechnicianVisitDTO.class)); }
        catch(Exception ignored) { }
    }
}