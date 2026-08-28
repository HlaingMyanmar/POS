package org.sspd.servicemgmt.technicianvisitoptions.dto;

import java.math.BigDecimal;

public record LocationPingRequest(
        String clientPingId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracy,
        String recordedAt
) {}
