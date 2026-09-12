package org.sspd.servicemgmt.dataevent;

import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;

class CustomerOrderEventTest {
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final DataEventPublisher publisher = new DataEventPublisher(messaging);
    @BeforeEach void begin() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void end() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void acceptanceIsDeliveredOnlyAfterCommitAndToItsCustomer() {
        publisher.publishToUser("customer:id:7", "/topic/customer-orders", "accepted");
        verifyNoInteractions(messaging);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(messaging).convertAndSendToUser("customer:id:7", "/topic/customer-orders", "accepted");
    }
    @Test void rolledBackOrderDoesNotNotify() {
        publisher.publishToUser("customer:id:7", "/topic/customer-orders", "accepted");
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(messaging);
    }
}
