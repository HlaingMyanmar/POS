package org.sspd.servicemgmt.chatoptions.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.chatoptions.model.ChatMessage;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findAllByOrderBySentAtAsc(Pageable pageable);
    List<ChatMessage> findByCustomerIdOrderBySentAtAsc(Integer customerId, Pageable pageable);
    List<ChatMessage> findByCustomerIdOrderBySentAtDesc(Integer customerId, Pageable pageable);

    @Query("select distinct m.customerId from ChatMessage m where m.customerId is not null")
    List<Integer> findCustomerIdsWithMessages();
}
