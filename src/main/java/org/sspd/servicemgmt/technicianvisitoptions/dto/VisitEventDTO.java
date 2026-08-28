package org.sspd.servicemgmt.technicianvisitoptions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VisitEventDTO(
        Long id,
        String eventType,
        BigDecimal latitude,
        BigDecimal longitude,
        String reasonCode,
        String note,
        LocalDateTime occurredAt
) {}
