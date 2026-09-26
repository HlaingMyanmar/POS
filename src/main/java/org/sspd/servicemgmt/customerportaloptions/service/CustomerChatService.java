package org.sspd.servicemgmt.customerportaloptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.chatoptions.dto.ChatMessageDTO;
import org.sspd.servicemgmt.chatoptions.model.ChatMessage;
import org.sspd.servicemgmt.chatoptions.repository.ChatMessageRepository;
import org.sspd.servicemgmt.companysettingoptions.service.CompanySettingsService;
import org.sspd.servicemgmt.customerportaloptions.dto.CustomerChatDTO;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomerChatService {
    private static final Set<String> GREETING_WORDS = Set.of(
            "hi", "hii", "hello", "helo", "hey", "မင်္ဂလာပါ", "ဟိုင်း", "ဟယ်လို");

    private final ChatMessageRepository messages;
    private final SimpMessagingTemplate messaging;
    private final CompanySettingsService companySettings;

    @Transactional(readOnly = true)
    public List<CustomerChatDTO> history() {
        int customerId = CustomerPortalAuth.require().getCustomerId();
        List<CustomerChatDTO> recent = new java.util.ArrayList<>(messages
                .findByCustomerIdOrderBySentAtDesc(customerId, PageRequest.of(0, 100))
                .stream().map(this::dto).toList());
        java.util.Collections.reverse(recent);
        return recent;
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
        broadcast(saved);
        sendGreetingIfNeeded(me.getCustomerId());
        return dto(saved);
    }

    /**
     * Auto-replies with the configured greeting the first time a customer opens the chat
     * with "hi", "မင်္ဂလာပါ" and similar — once per conversation, as an SSPD message.
     */
    private void sendGreetingIfNeeded(int customerId) {
        String configured = companySettings.getSettings().getChatGreeting();
        if (configured == null || configured.isBlank()) return;
        final String greeting = configured.trim();

        List<ChatMessage> recent = messages.findByCustomerIdOrderBySentAtDesc(customerId, PageRequest.of(0, 100));
        if (recent.isEmpty()) return;
        if (!isGreeting(recent.get(0).getContent())) return;
        boolean alreadyGreeted = recent.stream()
                .anyMatch(m -> !"CUSTOMER".equalsIgnoreCase(m.getSenderRole())
                        && greeting.equals(m.getContent() == null ? null : m.getContent().trim()));
        if (alreadyGreeted) return;

        ChatMessage auto = messages.save(ChatMessage.builder()
                .customerId(customerId)
                .senderUsername("sspd-chat-bot")
                .senderName("SSPD")
                .senderRole("ADMIN")
                .content(greeting)
                .sentAt(LocalDateTime.now())
                .build());
        broadcast(auto);
    }

    private static boolean isGreeting(String raw) {
        if (raw == null) return false;
        String text = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[!.,?။၊]+", "");
        return !text.isEmpty() && text.length() <= 30 && GREETING_WORDS.contains(text);
    }

    private void broadcast(ChatMessage message) {
        ChatMessageDTO payload = ChatMessageDTO.builder()
                .id(message.getId())
                .customerId(message.getCustomerId())
                .senderUsername(message.getSenderUsername())
                .senderName(message.getSenderName())
                .senderRole(message.getSenderRole())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .build();
        messaging.convertAndSend("/topic/chat", payload);
        if (message.getCustomerId() != null) {
            String customerUser = CustomerPortalAuth.usernameForCustomer(message.getCustomerId());
            messaging.convertAndSendToUser(customerUser, "/topic/chat", payload);
            java.util.Map<String, Object> portal = new java.util.LinkedHashMap<>();
            portal.put("id", message.getId());
            portal.put("customerId", message.getCustomerId());
            portal.put("senderId", message.getCustomerId());
            portal.put("content", message.getContent());
            portal.put("text", message.getContent());
            portal.put("senderName", message.getSenderName());
            portal.put("senderRole", message.getSenderRole());
            portal.put("isFromAdmin", false);
            String when = message.getSentAt() == null ? null : message.getSentAt().toString();
            portal.put("sentAt", when);
            portal.put("createdAt", when);
            messaging.convertAndSendToUser(customerUser, "/topic/chat", portal);
        }
    }

    private CustomerChatDTO dto(ChatMessage message) {
        boolean fromAdmin = !"CUSTOMER".equalsIgnoreCase(message.getSenderRole());
        String senderName = fromAdmin
                ? (message.getSenderName() != null && !message.getSenderName().isBlank()
                    ? message.getSenderName() : "SSPD")
                : message.getSenderName();
        return new CustomerChatDTO(
                message.getId(),
                message.getCustomerId(),
                message.getContent(),
                message.getSentAt(),
                fromAdmin,
                senderName);
    }
}
