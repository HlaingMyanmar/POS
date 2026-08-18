package org.sspd.servicemgmt.purchaseoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Integer> {
    Optional<Purchase> findByPurchaseCode(String purchaseCode);

    Optional<Purchase> findTopByOrderByIdDesc();

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.totalAmount), 0) from Purchase p")
    java.math.BigDecimal sumTotalAmount();

    @Query("SELECT p FROM Purchase p WHERE p.supplier.id = :supplierId")
    List<Purchase> findBySupplierId(@Param("supplierId") Integer supplierId);

    @Query("SELECT COALESCE(SUM(p.dueAmount), 0) FROM Purchase p WHERE p.supplier.id = :supplierId")
    BigDecimal sumDueAmountBySupplierId(@Param("supplierId") Integer supplierId);

    @Query("SELECT COALESCE(SUM(p.supplierCreditAmount), 0) FROM Purchase p WHERE p.supplier.id = :supplierId")
    BigDecimal sumSupplierCreditAmountBySupplierId(@Param("supplierId") Integer supplierId);

    @Query("SELECT p FROM Purchase p WHERE (:search IS NULL OR :search = '' OR LOWER(p.purchaseCode) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.code) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.phone) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.address) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.staff.name) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Purchase> findBySearch(@Param("search") String search, Pageable pageable);

    @Query("SELECT p FROM Purchase p WHERE " +
        "(:search IS NULL OR :search = '' OR LOWER(p.purchaseCode) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.code) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.phone) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.supplier.address) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(p.staff.name) LIKE LOWER(CONCAT('%',:search,'%'))) " +
        "AND (:from IS NULL OR p.purchaseDate >= :from) " +
        "AND (:to IS NULL OR p.purchaseDate <= :to)")
    Page<Purchase> findBySearchAndDateRange(@Param("search") String search, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @Query("""
        SELECT COUNT(p), COALESCE(SUM(p.totalAmount), 0), COALESCE(SUM(p.paidAmount), 0), COALESCE(SUM(p.dueAmount), 0)
        FROM Purchase p
        WHERE (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to IS NULL OR p.purchaseDate <= :to)
        """)
    List<Object[]> findStatsByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Purchase> findByDueAmountGreaterThan(BigDecimal amount);

    @Query("""
        SELECT COUNT(p) FROM Purchase p
        WHERE p.supplier.id  = :supplierId
          AND p.staff.id     = :staffId
          AND p.totalAmount  = :totalAmount
          AND p.purchaseDate >= :since
        """)
    long countRecentDuplicates(
        @Param("supplierId")  Integer supplierId,
        @Param("staffId")     Integer staffId,
        @Param("totalAmount") BigDecimal totalAmount,
        @Param("since")       LocalDateTime since
    );

    @Query("""
        SELECT FUNCTION('YEAR', p.purchaseDate), FUNCTION('MONTH', p.purchaseDate),
               COUNT(p), COALESCE(SUM(p.totalAmount), 0)
        FROM Purchase p
        WHERE (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to   IS NULL OR p.purchaseDate <  :to)
        GROUP BY FUNCTION('YEAR', p.purchaseDate), FUNCTION('MONTH', p.purchaseDate)
        ORDER BY FUNCTION('YEAR', p.purchaseDate) DESC, FUNCTION('MONTH', p.purchaseDate) DESC
        """)
    List<Object[]> monthlyPurchaseSummary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT p.supplier.id, p.supplier.name, COUNT(p), COALESCE(SUM(p.totalAmount), 0)
        FROM Purchase p
        WHERE (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to   IS NULL OR p.purchaseDate <  :to)
        GROUP BY p.supplier.id, p.supplier.name
        ORDER BY SUM(p.totalAmount) DESC
        """)
    List<Object[]> purchaseBySupplier(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(p), COALESCE(SUM(p.totalAmount), 0), COALESCE(SUM(p.dueAmount), 0)
        FROM Purchase p
        WHERE (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to   IS NULL OR p.purchaseDate <  :to)
        """)
    List<Object[]> purchaseTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
