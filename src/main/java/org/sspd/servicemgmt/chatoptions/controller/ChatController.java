package org.sspd.servicemgmt.chatoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth;
import org.sspd.servicemgmt.chatoptions.dto.ChatMessageDTO;
import org.sspd.servicemgmt.chatoptions.dto.CustomerConversationDTO;
import org.sspd.servicemgmt.customeroptions.repository.CustomerRepository;
import org.sspd.servicemgmt.chatoptions.model.ChatMessage;
import org.sspd.servicemgmt.chatoptions.repository.ChatMessageRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.Collections;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatRepo;
    private final SimpMessagingTemplate messaging;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('CAN_ACCESS_CUSTOMER_READ')")
    public ResponseEntity<ApiResponse<List<CustomerConversationDTO>>> conversations() {
        List<CustomerConversationDTO> conversations = chatRepo.findCustomerIdsWithMessages().stream()
                .map(customerId -> {
                    ChatMessage latest = chatRepo.findByCustomerIdOrderBySentAtDesc(customerId, PageRequest.of(0, 1))
                            .stream().findFirst().orElse(null);
                    if (latest == null) return null;
                    var customer = customerRepository.findById(customerId).orElse(null);
                    return new CustomerConversationDTO(customerId,
                            customer == null ? "Customer #" + customerId : customer.getName(),
                            customer == null ? null : customer.getPhone(),
                            latest.getContent(), latest.getSentAt(),
                            "CUSTOMER".equalsIgnoreCase(latest.getSenderRole()));
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(CustomerConversationDTO::lastAt).reversed())
                .toList();
        return ResponseEntity.ok(new ApiResponse<>(true, "Conversations", conversations));
    }

    @GetMapping("/customers/{customerId}/messages")
    @PreAuthorize("hasAuthority('CAN_ACCESS_CUSTOMER_READ')")
    public ResponseEntity<ApiResponse<List<ChatMessageDTO>>> customerMessages(@PathVariable Integer customerId) {
        List<ChatMessageDTO> messages = new java.util.ArrayList<>(chatRepo
                .findByCustomerIdOrderBySentAtDesc(customerId, PageRequest.of(0, 100))
                .stream().map(this::toDto).toList());
        Collections.reverse(messages);
        return ResponseEntity.ok(new ApiResponse<>(true, "Messages", messages));
    }

    // REST: load last 100 messages
    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageDTO>>> getMessages() {
        List<ChatMessageDTO> msgs = chatRepo
                .findAllByOrderBySentAtAsc(PageRequest.of(0, 100))
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse<>(true, "Messages", msgs));
    }

    // REST: send a message (mobile uses this)
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessageDTO>> sendRest(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody ChatMessageDTO req) {

        ChatMessageDTO saved = saveAndBroadcast(principal.getUsername(), req.getContent(), req.getCustomerId());
        return ResponseEntity.ok(new ApiResponse<>(true, "Sent", saved));
    }

    // WebSocket: send a message (web uses this; customer app uses /app/chat/send)
    @MessageMapping({"/chat.send", "/chat/send"})
    public void sendWs(@Payload ChatMessageDTO req, Principal principal) {
        Integer customerId = req.getCustomerId();
        if (CustomerPortalAuth.isCustomerUsername(principal.getName())) {
            customerId = CustomerPortalAuth.parseCustomerId(principal.getName());
        }
        saveAndBroadcast(principal.getName(), req.getContent(), customerId);
    }

    private ChatMessageDTO saveAndBroadcast(String username, String content, Integer customerId) {
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("Message is required");
        if (content.trim().length() > 2000) throw new IllegalArgumentException("Message is too long");
        if (CustomerPortalAuth.isCustomerUsername(username)) {
            Integer fromPrincipal = CustomerPortalAuth.parseCustomerId(username);
            if (fromPrincipal == null) throw new AccessDeniedException("Customer login required");
            customerId = fromPrincipal;
        }
        String displayName = username;
        String role = "";
        try {
            User user = userRepository.findByUsernameOrEmail(username, username).orElse(null);
            if (user != null && user.getName() != null && !user.getName().isBlank()) {
                displayName = user.getName();
            }
            if (user != null && !user.getRoles().isEmpty()) {
                role = user.getRoles().iterator().next().getName().replace("ROLE_", "");
            }
        } catch (Exception ignored) {}

        ChatMessage msg = ChatMessage.builder()
                .customerId(customerId)
                .senderUsername(username)
                .senderName(displayName)
                .senderRole(role == null || role.isBlank() ? "STAFF" : role)
                .content(content.trim())
                .sentAt(LocalDateTime.now())
                .build();
        chatRepo.save(msg);

        ChatMessageDTO dto = toDto(msg);
        // Staff inbox always listens on the shared topic.
        messaging.convertAndSend("/topic/chat", dto);
        if (customerId != null) {
            String customerUser = org.sspd.servicemgmt.customerportaloptions.support.CustomerPortalAuth
                    .usernameForCustomer(customerId);
            // Deliver both shapes: ChatMessageDTO (content/sentAt) and portal DTO aliases (text/createdAt).
            messaging.convertAndSendToUser(customerUser, "/topic/chat", dto);
            messaging.convertAndSendToUser(customerUser, "/topic/chat", customerFacingPayload(msg));
        }
        return dto;
    }

    private static java.util.Map<String, Object> customerFacingPayload(ChatMessage m) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", m.getId());
        payload.put("customerId", m.getCustomerId());
        payload.put("senderId", m.getCustomerId());
        payload.put("content", m.getContent());
        payload.put("text", m.getContent());
        payload.put("senderName", m.getSenderName());
        payload.put("senderRole", m.getSenderRole());
        payload.put("isFromAdmin", !"CUSTOMER".equalsIgnoreCase(m.getSenderRole()));
        String when = m.getSentAt() == null ? null : m.getSentAt().toString();
        payload.put("sentAt", when);
        payload.put("createdAt", when);
        return payload;
    }

    private ChatMessageDTO toDto(ChatMessage m) {
        return ChatMessageDTO.builder()
                .id(m.getId())
                .customerId(m.getCustomerId())
                .senderUsername(m.getSenderUsername())
                .senderName(m.getSenderName())
                .senderRole(m.getSenderRole())
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .build();
    }
}
