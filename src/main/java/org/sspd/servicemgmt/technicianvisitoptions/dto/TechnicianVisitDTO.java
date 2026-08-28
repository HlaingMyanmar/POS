package org.sspd.servicemgmt.technicianvisitoptions.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TechnicianVisitDTO(
        Long id,
        Integer staffId,
        String staffName,
        Integer jobId,
        String jobNo,
        Integer customerId,
        String customerName,
        String status,
        String motionStatus,
        Boolean needsReason,
        LocalDateTime startedAt,
        LocalDateTime arrivedAt,
        LocalDateTime endedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracy,
        LocalDateTime recordedAt,
        BigDecimal customerLatitude,
        BigDecimal customerLongitude,
        Double distanceMeters,
        List<VisitEventDTO> events
) {}
