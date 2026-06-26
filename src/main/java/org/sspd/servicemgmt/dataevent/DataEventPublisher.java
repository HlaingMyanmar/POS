package org.sspd.servicemgmt.dataevent;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataEventPublisher {

    private final SimpMessagingTemplate messaging;

    public void broadcast(String entity, String action, String resourceId) {
        messaging.convertAndSend(
            "/topic/data-events",
            new DataEventDTO(entity, action, resourceId)
        );
    }
}
