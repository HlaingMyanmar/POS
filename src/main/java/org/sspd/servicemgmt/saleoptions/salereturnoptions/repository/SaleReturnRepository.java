package org.sspd.servicemgmt.saleoptions.salereturnoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleReturnRepository extends JpaRepository<SaleReturn, Integer> {
    Optional<SaleReturn> findByReturnCode(String returnCode);
    Optional<SaleReturn> findTopByOrderByIdDesc();
    @Query("SELECT r FROM SaleReturn r WHERE r.sale.id = :saleId AND (r.deleted = false OR r.deleted IS NULL)")
    List<SaleReturn> findAllBySaleIdAndDeletedFalse(@Param("saleId") Integer saleId);

    @Query("SELECT r FROM SaleReturn r WHERE (r.deleted = false OR r.deleted IS NULL)")
    List<SaleReturn> findAllByDeletedFalse();

    @Query("SELECT r FROM SaleReturn r WHERE (r.deleted = false OR r.deleted IS NULL) AND (:search IS NULL OR :search = '' OR LOWER(r.returnCode) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.reason) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.sale.customer.name) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<SaleReturn> findBySearch(@Param("search") String search, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(r.totalReturnAmount), 0)
        FROM SaleReturn r
        WHERE (r.deleted = false OR r.deleted IS NULL)
          AND (:from IS NULL OR r.returnDate >= :from)
          AND (:to   IS NULL OR r.returnDate <= :to)
        """)
    BigDecimal sumInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
