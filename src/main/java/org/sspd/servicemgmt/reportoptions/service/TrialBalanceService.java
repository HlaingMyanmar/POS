package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;
import org.sspd.servicemgmt.reportoptions.dto.TrialBalanceDTO;
import org.sspd.servicemgmt.reportoptions.dto.TrialBalanceLineItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TrialBalanceService {

    private final JournalDetailRepository repo;

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @Transactional(readOnly = true)
    public TrialBalanceDTO getTrialBalance(LocalDate asOf) {
        LocalDateTime asOfDt = asOf.atTime(23, 59, 59);

        List<TrialBalanceLineItem> lines = repo.trialBalance(asOfDt).stream()
                .map(row -> new TrialBalanceLineItem(
                        (String) row[0],
                        (String) row[1],
                        row[2].toString(),
                        (BigDecimal) row[3],
                        (BigDecimal) row[4]))
                .filter(l -> l.getTotalDebit().compareTo(BigDecimal.ZERO) > 0
                        || l.getTotalCredit().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        BigDecimal totalDr = lines.stream()
                .map(TrialBalanceLineItem::getTotalDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCr = lines.stream()
                .map(TrialBalanceLineItem::getTotalCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TrialBalanceDTO.builder()
                .asOf(asOf)
                .lines(lines)
                .grandTotalDebit(totalDr)
                .grandTotalCredit(totalCr)
                .balanced(totalDr.compareTo(totalCr) == 0)
                .build();
    }
}
