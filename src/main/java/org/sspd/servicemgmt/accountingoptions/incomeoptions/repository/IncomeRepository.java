package org.sspd.servicemgmt.accountingoptions.incomeoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.accountingoptions.incomeoptions.model.Income;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Integer> {
    Optional<Income> findTopByOrderByIdDesc();

    @Query("""
        SELECT COALESCE(SUM(i.amount), 0)
        FROM Income i
        WHERE (:from IS NULL OR i.incomeDate >= :from)
          AND (:to   IS NULL OR i.incomeDate <= :to)
        """)
    BigDecimal sumInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
