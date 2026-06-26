package org.sspd.servicemgmt.journaloption.detail.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.coaoptions.enums.AccountType;
import org.sspd.servicemgmt.journaloption.detail.model.JournalDetail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JournalDetailRepository extends JpaRepository<JournalDetail, Integer> {

    List<JournalDetail> findByJournalEntryId(Integer journalId);
    List<JournalDetail> findByAccountId(Integer accountId);
    boolean existsByAccountId(Integer accountId);

    // တစ်ခုတည်းသော account code အတွက် net credit (Income normal balance)
    @Query("SELECT COALESCE(SUM(jd.credit) - SUM(jd.debit), 0) FROM JournalDetail jd " +
           "WHERE jd.account.code = :code AND jd.journalEntry.entryDate BETWEEN :from AND :to")
    BigDecimal netCreditByCode(@Param("code") String code,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    // တစ်ခုတည်းသော account code အတွက် net debit (Expense normal balance)
    @Query("SELECT COALESCE(SUM(jd.debit) - SUM(jd.credit), 0) FROM JournalDetail jd " +
           "WHERE jd.account.code = :code AND jd.journalEntry.entryDate BETWEEN :from AND :to")
    BigDecimal netDebitByCode(@Param("code") String code,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    // Other Income: Income type မှ သတ်မှတ်ထားသော codes များ ဖယ်၍ account တစ်ခုချင်းစီ SUM
    @Query("SELECT jd.account.code, jd.account.accountName, " +
           "COALESCE(SUM(jd.credit) - SUM(jd.debit), 0) " +
           "FROM JournalDetail jd " +
           "WHERE jd.account.accountType = :type " +
           "AND jd.account.code NOT IN :excludeCodes " +
           "AND jd.journalEntry.entryDate BETWEEN :from AND :to " +
           "GROUP BY jd.account.code, jd.account.accountName " +
           "ORDER BY jd.account.code")
    List<Object[]> sumCreditNetExclude(@Param("type") AccountType type,
                                       @Param("excludeCodes") List<String> excludeCodes,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    // Operating Expenses: Expense type မှ သတ်မှတ်ထားသော codes များ ဖယ်၍ account တစ်ခုချင်းစီ SUM
    @Query("SELECT jd.account.code, jd.account.accountName, " +
           "COALESCE(SUM(jd.debit) - SUM(jd.credit), 0) " +
           "FROM JournalDetail jd " +
           "WHERE jd.account.accountType = :type " +
           "AND jd.account.code NOT IN :excludeCodes " +
           "AND jd.journalEntry.entryDate BETWEEN :from AND :to " +
           "GROUP BY jd.account.code, jd.account.accountName " +
           "ORDER BY jd.account.code")
    List<Object[]> sumDebitNetExclude(@Param("type") AccountType type,
                                      @Param("excludeCodes") List<String> excludeCodes,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    // ── Trial Balance ───────────────────────────────────────────────────────────

    @Query("SELECT jd.account.code, jd.account.accountName, jd.account.accountType, " +
           "COALESCE(SUM(jd.debit), 0), COALESCE(SUM(jd.credit), 0) " +
           "FROM JournalDetail jd " +
           "WHERE jd.journalEntry.entryDate <= :asOf " +
           "GROUP BY jd.account.code, jd.account.accountName, jd.account.accountType " +
           "ORDER BY jd.account.accountType, jd.account.code")
    List<Object[]> trialBalance(@Param("asOf") LocalDateTime asOf);

    // ── Balance Sheet ───────────────────────────────────────────────────────────

    // Net DR balance per account (Assets: DR - CR)
    @Query("SELECT jd.account.code, jd.account.accountName, " +
           "COALESCE(SUM(jd.debit) - SUM(jd.credit), 0) " +
           "FROM JournalDetail jd " +
           "WHERE jd.account.accountType = :type " +
           "AND jd.journalEntry.entryDate <= :asOf " +
           "GROUP BY jd.account.code, jd.account.accountName " +
           "ORDER BY jd.account.code")
    List<Object[]> netDebitBalanceByType(@Param("type") AccountType type,
                                         @Param("asOf") LocalDateTime asOf);

    // Net CR balance per account (Liabilities/Equity: CR - DR)
    @Query("SELECT jd.account.code, jd.account.accountName, " +
           "COALESCE(SUM(jd.credit) - SUM(jd.debit), 0) " +
           "FROM JournalDetail jd " +
           "WHERE jd.account.accountType = :type " +
           "AND jd.journalEntry.entryDate <= :asOf " +
           "GROUP BY jd.account.code, jd.account.accountName " +
           "ORDER BY jd.account.code")
    List<Object[]> netCreditBalanceByType(@Param("type") AccountType type,
                                          @Param("asOf") LocalDateTime asOf);

    // Aggregate net debit for a type (for P/L in balance sheet)
    @Query("SELECT COALESCE(SUM(jd.debit) - SUM(jd.credit), 0) FROM JournalDetail jd " +
           "WHERE jd.account.accountType = :type AND jd.journalEntry.entryDate <= :asOf")
    BigDecimal totalNetDebitByType(@Param("type") AccountType type,
                                   @Param("asOf") LocalDateTime asOf);

    // Aggregate net credit for a type (for P/L in balance sheet)
    @Query("SELECT COALESCE(SUM(jd.credit) - SUM(jd.debit), 0) FROM JournalDetail jd " +
           "WHERE jd.account.accountType = :type AND jd.journalEntry.entryDate <= :asOf")
    BigDecimal totalNetCreditByType(@Param("type") AccountType type,
                                    @Param("asOf") LocalDateTime asOf);
}
