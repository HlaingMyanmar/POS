package org.sspd.servicemgmt.reportoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.reportoptions.dto.StaffReportDTO;
import org.sspd.servicemgmt.saleoptions.repository.SaleRepository;
import org.sspd.servicemgmt.servicejoboptions.repository.ServiceJobRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class StaffReportController {

    private final SaleRepository saleRepo;
    private final ServiceJobRepository serviceJobRepo;

    /**
     * GET /api/v1/reports/staff?month=2026-05
     * Monthly staff report (used by mobile app).
     */
    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<StaffReportDTO>>> staffReport(
            @RequestParam(defaultValue = "") String month) {

        YearMonth ym = month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        LocalDateTime from = ym.atDay(1).atStartOfDay();
        LocalDateTime to   = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<StaffReportDTO> result = buildReport(from, to);
        return ResponseEntity.ok(new ApiResponse<>(true, "Staff report for " + ym, result));
    }

    /**
     * GET /api/v1/reports/staff/performance?from=2026-01-01&to=2026-05-31
     * Date-range staff performance report (used by web UI).
     */
    @GetMapping("/staff/performance")
    public ResponseEntity<ApiResponse<List<StaffReportDTO>>> staffPerformance(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime dtFrom = from == null || from.isBlank()
                ? LocalDate.now().withDayOfMonth(1).atStartOfDay()
                : LocalDate.parse(from).atStartOfDay();
        LocalDateTime dtTo = to == null || to.isBlank()
                ? dtFrom.plusMonths(1)
                : LocalDate.parse(to).plusDays(1).atStartOfDay();

        List<StaffReportDTO> result = buildReport(dtFrom, dtTo);
        return ResponseEntity.ok(new ApiResponse<>(true, "Staff performance report", result));
    }

    private List<StaffReportDTO> buildReport(LocalDateTime from, LocalDateTime to) {
        Map<Integer, StaffReportDTO> map = new LinkedHashMap<>();

        for (Object[] row : saleRepo.staffSaleStats(from, to)) {
            Integer id     = (Integer)    row[0];
            String  name   = (String)     row[1];
            String  role   = (String)     row[2];
            long    count  = ((Number)    row[3]).longValue();
            BigDecimal amt = (BigDecimal) row[4];

            map.put(id, StaffReportDTO.builder()
                    .staffId(id).staffName(name).staffRole(role)
                    .salesCount(count).salesAmount(amt)
                    .serviceJobsCount(0).completedJobsCount(0)
                    .cancelledJobsCount(0).reworkJobsCount(0).inProgressJobsCount(0)
                    .serviceJobsAmount(BigDecimal.ZERO)
                    .completionRate(0.0)
                    .build());
        }

        for (Object[] row : serviceJobRepo.staffServiceStats(from, to)) {
            Integer id         = (Integer)    row[0];
            String  name       = (String)     row[1];
            String  role       = (String)     row[2];
            long    total      = ((Number)    row[3]).longValue();
            long    completed  = ((Number)    row[4]).longValue();
            BigDecimal amt     = (BigDecimal) row[5];
            long    cancelled  = ((Number)    row[6]).longValue();
            long    reworks    = ((Number)    row[7]).longValue();
            long    inProgress = ((Number)    row[8]).longValue();

            double rate = total > 0 ? Math.round((completed * 100.0 / total) * 10) / 10.0 : 0.0;

            StaffReportDTO dto = map.computeIfAbsent(id, k -> StaffReportDTO.builder()
                    .staffId(id).staffName(name).staffRole(role)
                    .salesCount(0).salesAmount(BigDecimal.ZERO)
                    .build());
            dto.setServiceJobsCount(total);
            dto.setCompletedJobsCount(completed);
            dto.setCancelledJobsCount(cancelled);
            dto.setReworkJobsCount(reworks);
            dto.setInProgressJobsCount(inProgress);
            dto.setServiceJobsAmount(amt);
            dto.setCompletionRate(rate);
        }

        List<StaffReportDTO> result = new ArrayList<>(map.values());
        result.sort((a, b) -> a.getStaffName().compareToIgnoreCase(b.getStaffName()));
        return result;
    }
}
