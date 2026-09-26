package org.sspd.servicemgmt.chatoptions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    private Integer customerId;
    private String senderUsername;
    private String senderName;
    private String senderRole;
    private String content;
    /** Always emit ISO-8601 text so mobile STOMP clients can parse without Jackson arrays. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;
}
