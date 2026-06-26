package org.sspd.servicemgmt.chatoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessageDTO {
    private Long id;
    private String senderUsername;
    private String senderName;
    private String senderRole;
    private String content;
    private LocalDateTime sentAt;
}
