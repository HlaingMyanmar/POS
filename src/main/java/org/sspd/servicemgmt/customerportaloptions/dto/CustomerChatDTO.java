package org.sspd.servicemgmt.customerportaloptions.dto;

import java.time.LocalDateTime;

public record CustomerChatDTO(Long id, Integer senderId, String text, LocalDateTime createdAt, boolean isFromAdmin) {}
