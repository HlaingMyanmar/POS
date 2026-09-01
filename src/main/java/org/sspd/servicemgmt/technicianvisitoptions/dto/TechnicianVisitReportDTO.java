package org.sspd.servicemgmt.technicianvisitoptions.dto;

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
        LocalDateTime startedAt,
        LocalDateTime arrivedAt,
        LocalDateTime leftCustomerAt,
        LocalDateTime endedAt,
        Long outboundMinutes,
        Long onSiteMinutes,
        Long returnMinutes,
        Long totalMinutes,
        Double actualDistanceMeters,
        Double arrivalDistanceMeters,
        Boolean arrivedNearCustomer,
        int stopCount,
        long stopMinutes,
        List<String> stopReasons,
        int pingCount,
        long maxPingGapMinutes,
        String gpsQualityIssues
) {
}
