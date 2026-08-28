package org.sspd.servicemgmt.technicianvisitoptions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VisitEventDTO(
        Long id,
        String eventType,
        BigDecimal latitude,
        BigDecimal longitude,
        String reasonCode,
        String note,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime occurredAt
) {}
