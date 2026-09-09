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
        publishTopic("/topic/data-events", payload);
    }

    public void publishToUser(String username, String destination, Object payload) {
        afterCommit(() -> messaging.convertAndSendToUser(username, destination, payload));
    }

    /** Broadcast to any STOMP topic after the current transaction commits. */
    public void publishTopic(String topic, Object payload) {
        afterCommit(() -> messaging.convertAndSend(topic, payload));
    }

    public void publishCustomerOrder(String type, Integer orderId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        if (orderId != null) payload.put("orderId", orderId);
        publishTopic("/topic/customer-order", payload);
    }

    private void afterCommit(Runnable send) {
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
