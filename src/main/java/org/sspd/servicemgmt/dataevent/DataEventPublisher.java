package org.sspd.servicemgmt.dataevent;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        Runnable send = () -> messaging.convertAndSend("/topic/data-events", payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
