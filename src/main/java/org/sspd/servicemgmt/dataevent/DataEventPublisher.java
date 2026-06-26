package org.sspd.servicemgmt.dataevent;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataEventPublisher {

    private final SimpMessagingTemplate messaging;

    public void broadcast(String entity, String action, String resourceId) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("entity", entity);
        payload.put("action", action);
        payload.put("resourceId", resourceId);

        messaging.convertAndSend("/topic/data-events", payload);
    }
}
