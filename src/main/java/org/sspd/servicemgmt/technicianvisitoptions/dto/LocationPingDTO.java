package org.sspd.servicemgmt.technicianvisitoptions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationPingDTO(
        Long id,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracy,
        LocalDateTime recordedAt
) {
}
