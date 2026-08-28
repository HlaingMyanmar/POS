package org.sspd.servicemgmt.technicianvisitoptions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationPingDTO(
        Long id,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracy,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime recordedAt
) {}
