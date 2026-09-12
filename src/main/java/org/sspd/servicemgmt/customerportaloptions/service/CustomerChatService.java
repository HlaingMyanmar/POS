package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.chatoptions.model.ChatMessage;
import org.sspd.servicemgmt.chatoptions.repository.ChatMessageRepository;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerChatDTO;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerChatService {
    private final ChatMessageRepository messages;

    @Transactional(readOnly = true)
    public List<CustomerChatDTO> history() {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        return messages.findByCustomerIdOrderBySentAtAsc(customerId, PageRequest.of(0, 100))
                .stream().map(this::dto).toList();
    }

    @Transactional
    public CustomerChatDTO send(String raw) {
        var me = CustomerPortalAuth.require();
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Message is required");
        if (text.length() > 2000) throw new IllegalArgumentException("Message is too long");
        ChatMessage saved = messages.save(ChatMessage.builder()
                .customerId(me.getCustomerId()).senderUsername(me.getUsername()).senderName(me.getName())
                .senderRole("CUSTOMER").content(text).sentAt(LocalDateTime.now()).build());
        CustomerChatDTO dto = dto(saved);
        return dto;
    }

    private CustomerChatDTO dto(ChatMessage message) {
        return new CustomerChatDTO(message.getId(), message.getCustomerId(), message.getContent(),
                message.getSentAt(), !"CUSTOMER".equalsIgnoreCase(message.getSenderRole()));
    }
}
