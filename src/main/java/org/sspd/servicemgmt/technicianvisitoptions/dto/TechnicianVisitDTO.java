package org.sspd.servicemgmt.technicianvisitoptions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

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
        String purpose,
        String outcome,
        String outcomeNote,
        String status,
        String motionStatus,
        Boolean needsReason,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime arrivedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime leftCustomerAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endedAt,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracy,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime recordedAt,
        BigDecimal customerLatitude,
        BigDecimal customerLongitude,
        Double distanceMeters,
        List<VisitEventDTO> events
) {}
