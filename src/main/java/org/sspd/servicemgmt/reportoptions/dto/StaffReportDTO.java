package org.sspd.servicemgmt.reportoptions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StaffReportDTO {
    private Integer staffId;
    private String staffName;
    private String staffRole;

    // Sales stats (Cashier / any staff who makes sales)
    private long salesCount;
    private BigDecimal salesAmount;

    // Service job stats (Technician / assigned staff)
    private long serviceJobsCount;
    private long completedJobsCount;
    private long cancelledJobsCount;
    private long reworkJobsCount;
    private long inProgressJobsCount;
    private BigDecimal serviceJobsAmount;
    private Double completionRate;
}
