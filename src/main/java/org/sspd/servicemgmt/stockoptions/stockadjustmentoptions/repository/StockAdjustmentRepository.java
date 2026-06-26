package org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.stockoptions.stockadjustmentoptions.model.StockAdjustment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Integer> {
    List<StockAdjustment> findByProductId(Integer productId);

    @Query(value = "SELECT sa FROM StockAdjustment sa JOIN FETCH sa.product JOIN FETCH sa.staff WHERE (:search IS NULL OR :search = '' OR LOWER(sa.product.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(sa.product.productCode) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(sa.staff.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(sa.reason) LIKE LOWER(CONCAT('%',:search,'%')))",
           countQuery = "SELECT COUNT(sa) FROM StockAdjustment sa JOIN sa.product JOIN sa.staff WHERE (:search IS NULL OR :search = '' OR LOWER(sa.product.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(sa.product.productCode) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(sa.staff.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(sa.reason) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<StockAdjustment> findBySearch(@Param("search") String search, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN sa.qtyChange < 0
                 THEN (-sa.qtyChange) * COALESCE(sa.product.costPrice, 0)
                 ELSE 0 END
        ), 0)
        FROM StockAdjustment sa
        WHERE (:from IS NULL OR sa.createdAt >= :from)
          AND (:to   IS NULL OR sa.createdAt <= :to)
        """)
    BigDecimal sumLossValueInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}