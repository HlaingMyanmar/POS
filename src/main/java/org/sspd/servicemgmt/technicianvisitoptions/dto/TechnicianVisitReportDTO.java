package org.sspd.servicemgmt.technicianvisitoptions.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record TechnicianVisitReportDTO(
        Long visitId,
        Integer staffId,
        String staffName,
        Integer jobId,
        String jobNo,
        Integer customerId,
        String customerName,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime arrivedAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime leftCustomerAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endedAt,
        Long outboundMinutes,
        Long onSiteMinutes,
        Long returnMinutes,
        Long totalMinutes,
        Double actualDistanceMeters,
        Double arrivalDistanceMeters,
        Boolean arrivalVerified,
        Integer stopCount,
        Long stopMinutes,
        List<String> stopReasons,
        Integer gpsPointCount,
        Long maxGpsGapMinutes,
        String gpsException
) {}
