package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Integer> {
    Optional<PurchaseReturn> findByReturnNo(String returnNo);
    Optional<PurchaseReturn> findTopByOrderByIdDesc();
    List<PurchaseReturn> findByPurchaseId(Integer purchaseId);

    @Query("SELECT r FROM PurchaseReturn r WHERE (:search IS NULL OR :search = '' OR LOWER(r.returnNo) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.reason) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.purchase.supplier.name) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<PurchaseReturn> findBySearch(@Param("search") String search, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(r.totalReturnAmount), 0)
        FROM PurchaseReturn r
        WHERE (:from IS NULL OR r.returnDate >= :from)
          AND (:to   IS NULL OR r.returnDate <= :to)
        """)
    BigDecimal sumInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
