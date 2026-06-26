package org.sspd.servicemgmt.chatoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.chatoptions.dto.ChatMessageDTO;
import org.sspd.servicemgmt.chatoptions.model.ChatMessage;
import org.sspd.servicemgmt.chatoptions.repository.ChatMessageRepository;
import org.sspd.servicemgmt.rbacoptions.useroptions.model.User;
import org.sspd.servicemgmt.rbacoptions.useroptions.repository.UserRepository;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatRepo;
    private final SimpMessagingTemplate messaging;
    private final UserRepository userRepository;

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

        ChatMessageDTO saved = saveAndBroadcast(principal.getUsername(), req.getContent());
        return ResponseEntity.ok(new ApiResponse<>(true, "Sent", saved));
    }

    // WebSocket: send a message (web uses this)
    @MessageMapping("/chat.send")
    public void sendWs(@Payload ChatMessageDTO req, Principal principal) {
        saveAndBroadcast(principal.getName(), req.getContent());
    }

    private ChatMessageDTO saveAndBroadcast(String username, String content) {
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
                .senderUsername(username)
                .senderName(displayName)
                .senderRole(role)
                .content(content.trim())
                .sentAt(LocalDateTime.now())
                .build();
        chatRepo.save(msg);

        ChatMessageDTO dto = toDto(msg);
        messaging.convertAndSend("/topic/chat", dto);
        return dto;
    }

    private ChatMessageDTO toDto(ChatMessage m) {
        return ChatMessageDTO.builder()
                .id(m.getId())
                .senderUsername(m.getSenderUsername())
                .senderName(m.getSenderName())
                .senderRole(m.getSenderRole())
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .build();
    }
}
