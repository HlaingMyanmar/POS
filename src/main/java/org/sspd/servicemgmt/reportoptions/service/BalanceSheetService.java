package org.sspd.servicemgmt.reportoptions.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sspd.servicemgmt.accountingoptions.coaoptions.AccountCode;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.journaloption.detail.repository.JournalDetailRepository;
import org.sspd.servicemgmt.reportoptions.dto.BalanceSheetDTO;
import org.sspd.servicemgmt.reportoptions.dto.BalanceSheetLineItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BalanceSheetService {

    private final JournalDetailRepository repo;

    @PreAuthorize("hasAuthority('CAN_ACCESS_REPORT_READ')")
    @Transactional(readOnly = true)
    public BalanceSheetDTO getBalanceSheet(LocalDate asOf) {
        LocalDateTime asOfDt = asOf.atTime(23, 59, 59);

        // Assets: normal balance DR — net = DR - CR (positive means asset balance)
        List<Object[]> assetRows = repo.netDebitBalanceByType(AccountType.Asset, asOfDt);
        List<BalanceSheetLineItem> assets = toItems(assetRows);
        BigDecimal totalAssets = sumRows(assetRows);

        // Liabilities: normal balance CR — net = CR - DR
        List<Object[]> liabilityRows = repo.netCreditBalanceByType(AccountType.Liability, asOfDt);
        List<BalanceSheetLineItem> liabilities = toItems(liabilityRows);
        BigDecimal totalLiabilities = sumRows(liabilityRows);

        // Equity accounts (Share Capital, Retained Earnings, etc.)
        List<Object[]> equityRows = repo.netCreditBalanceByType(AccountType.Equity, asOfDt);
        BigDecimal postedCurrentYearPnL = findBalanceByCode(equityRows, AccountCode.CURRENT_YEAR_PNL);
        List<BalanceSheetLineItem> equityItems = toItemsExcludingCode(equityRows, AccountCode.CURRENT_YEAR_PNL);
        BigDecimal equityFromAccounts = sumRowsExcludingCode(equityRows, AccountCode.CURRENT_YEAR_PNL);

        // Accumulated P/L (all income – all expenses, from beginning up to asOf)
        BigDecimal totalIncome  = safe(repo.totalNetCreditByType(AccountType.Income,  asOfDt));
        BigDecimal totalExpense = safe(repo.totalNetDebitByType(AccountType.Expense, asOfDt));
        BigDecimal calculatedCurrentYearPnL = totalIncome.subtract(totalExpense);

        // If current-year P/L is already posted to EQU-003, use posted amount and avoid double counting.
        BigDecimal currentYearPnL = postedCurrentYearPnL.compareTo(BigDecimal.ZERO) != 0
                ? postedCurrentYearPnL
                : calculatedCurrentYearPnL;

        BigDecimal totalEquity               = equityFromAccounts.add(currentYearPnL);
        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);

        return BalanceSheetDTO.builder()
                .asOf(asOf)
                .assets(assets)
                .totalAssets(totalAssets)
                .liabilities(liabilities)
                .totalLiabilities(totalLiabilities)
                .equityItems(equityItems)
                .currentYearPnL(currentYearPnL)
                .totalEquity(totalEquity)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .balanced(totalAssets.compareTo(totalLiabilitiesAndEquity) == 0)
                .build();
    }

    private List<BalanceSheetLineItem> toItems(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new Object[]{
                        (String) row[0],
                        (String) row[1],
                        safe((BigDecimal) row[2])
                })
                .filter(row -> ((BigDecimal) row[2]).compareTo(BigDecimal.ZERO) != 0)
                .map(row -> new BalanceSheetLineItem(
                        (String) row[0],
                        (String) row[1],
                        (BigDecimal) row[2]))
                .toList();
    }

    private List<BalanceSheetLineItem> toItemsExcludingCode(List<Object[]> rows, String excludedCode) {
        return rows.stream()
                .filter(row -> !excludedCode.equals(row[0]))
                .map(row -> new Object[]{
                        (String) row[0],
                        (String) row[1],
                        safe((BigDecimal) row[2])
                })
                .filter(row -> ((BigDecimal) row[2]).compareTo(BigDecimal.ZERO) != 0)
                .map(row -> new BalanceSheetLineItem(
                        (String) row[0],
                        (String) row[1],
                        (BigDecimal) row[2]))
                .toList();
    }

    private BigDecimal findBalanceByCode(List<Object[]> rows, String code) {
        return rows.stream()
                .filter(row -> code.equals(row[0]))
                .findFirst()
                .map(row -> safe((BigDecimal) row[2]))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal sumRows(List<Object[]> rows) {
        return rows.stream()
                .map(row -> safe((BigDecimal) row[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumRowsExcludingCode(List<Object[]> rows, String excludedCode) {
        return rows.stream()
                .filter(row -> !excludedCode.equals(row[0]))
                .map(row -> safe((BigDecimal) row[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sum(List<BalanceSheetLineItem> items) {
        return items.stream()
                .map(BalanceSheetLineItem::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
