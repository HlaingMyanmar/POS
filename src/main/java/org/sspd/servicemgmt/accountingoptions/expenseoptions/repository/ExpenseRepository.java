package org.sspd.servicemgmt.accountingoptions.expenseoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.expenseoptions.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Integer> {
    Optional<Expense> findTopByOrderByIdDesc();

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE (:from IS NULL OR e.expenseDate >= :from)
          AND (:to   IS NULL OR e.expenseDate <= :to)
        """)
    BigDecimal sumInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
