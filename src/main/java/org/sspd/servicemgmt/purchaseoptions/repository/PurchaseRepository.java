package org.sspd.servicemgmt.purchaseoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.model.Purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Integer> {
    Optional<Purchase> findByPurchaseCode(String purchaseCode);

    Optional<Purchase> findTopByOrderByIdDesc();

    @Query("""
        SELECT COUNT(p) FROM Purchase p
        WHERE p.supplier.id = :supplierId
          AND LOWER(TRIM(p.supplierInvoiceNo)) = LOWER(TRIM(:invoiceNo))
          AND (:excludeId IS NULL OR p.id <> :excludeId)
          AND (p.status IS NULL OR p.status <> org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CANCELLED)
        """)
    long countSupplierInvoiceDuplicates(@Param("supplierId") Integer supplierId,
                                        @Param("invoiceNo") String invoiceNo,
                                        @Param("excludeId") Integer excludeId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(p.totalAmount), 0) from Purchase p")
    java.math.BigDecimal sumTotalAmount();

    @Query("SELECT p FROM Purchase p WHERE p.supplier.id = :supplierId")
    List<Purchase> findBySupplierId(@Param("supplierId") Integer supplierId);

    @Query("SELECT COALESCE(SUM(p.dueAmount), 0) FROM Purchase p WHERE " +
           "(p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED) " +
           "AND p.supplier.id = :supplierId")
    BigDecimal sumDueAmountBySupplierId(@Param("supplierId") Integer supplierId);

    @Query("SELECT COALESCE(SUM(p.supplierCreditAmount), 0) FROM Purchase p WHERE " +
           "(p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED) " +
           "AND p.supplier.id = :supplierId")
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
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to IS NULL OR p.purchaseDate <= :to)
        """)
    List<Object[]> findStatsByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT FUNCTION('YEAR', p.purchaseDate), FUNCTION('MONTH', p.purchaseDate), FUNCTION('DAY', p.purchaseDate),
               COALESCE(SUM(p.totalAmount), 0), COALESCE(SUM(p.paidAmount), 0),
               COALESCE(SUM(p.dueAmount), 0), COUNT(p)
        FROM Purchase p
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to IS NULL OR p.purchaseDate <= :to)
        GROUP BY FUNCTION('YEAR', p.purchaseDate), FUNCTION('MONTH', p.purchaseDate), FUNCTION('DAY', p.purchaseDate)
        ORDER BY FUNCTION('YEAR', p.purchaseDate), FUNCTION('MONTH', p.purchaseDate), FUNCTION('DAY', p.purchaseDate)
        """)
    List<Object[]> findDailyTrendByDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT s.name, s.code, SUM(p.netAmount) as totalAmount, COUNT(p) as count
        FROM Purchase p
        JOIN p.supplier s
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to IS NULL OR p.purchaseDate <= :to)
        GROUP BY s.id, s.name, s.code
        ORDER BY totalAmount DESC
        """)
    List<Object[]> findTopSuppliersByAmount(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Purchase> findByDueAmountGreaterThan(BigDecimal amount);

    @Query("""
        SELECT p FROM Purchase p
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND p.dueAmount > 0
        ORDER BY p.dueDate ASC
        """)
    List<Purchase> findActivePayables();

    @Query("""
        SELECT p FROM Purchase p
        WHERE p.supplier.id = :supplierId
          AND (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND p.dueAmount > 0
        ORDER BY CASE WHEN p.dueDate IS NULL THEN 1 ELSE 0 END, p.dueDate ASC, p.purchaseDate ASC, p.id ASC
        """)
    List<Purchase> findSupplierPayablesFifo(@Param("supplierId") Integer supplierId);

    @Query("""
        SELECT p FROM Purchase p
        WHERE p.supplier.id = :supplierId
          AND (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND p.supplierCreditAmount > 0
        ORDER BY p.purchaseDate ASC, p.id ASC
        """)
    List<Purchase> findSupplierCreditSourcesFifo(@Param("supplierId") Integer supplierId);

    @Query("""
        SELECT p FROM Purchase p
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND p.dueAmount > 0
          AND p.dueDate IS NOT NULL AND p.dueDate < :today
        ORDER BY p.dueDate ASC
        """)
    List<Purchase> findOverduePayables(@Param("today") LocalDate today);

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
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to   IS NULL OR p.purchaseDate <  :to)
        GROUP BY FUNCTION('YEAR', p.purchaseDate), FUNCTION('MONTH', p.purchaseDate)
        ORDER BY FUNCTION('YEAR', p.purchaseDate) DESC, FUNCTION('MONTH', p.purchaseDate) DESC
        """)
    List<Object[]> monthlyPurchaseSummary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT p.supplier.id, p.supplier.name, COUNT(p), COALESCE(SUM(p.totalAmount), 0)
        FROM Purchase p
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to   IS NULL OR p.purchaseDate <  :to)
        GROUP BY p.supplier.id, p.supplier.name
        ORDER BY SUM(p.totalAmount) DESC
        """)
    List<Object[]> purchaseBySupplier(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(p), COALESCE(SUM(p.totalAmount), 0), COALESCE(SUM(p.dueAmount), 0)
        FROM Purchase p
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to   IS NULL OR p.purchaseDate <  :to)
        """)
    List<Object[]> purchaseTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(p), COALESCE(SUM(p.netAmount), 0), COALESCE(SUM(p.paidAmount), 0), COALESCE(SUM(p.dueAmount), 0),
               COALESCE(SUM(p.taxAmount), 0), COALESCE(SUM(p.withholdingTaxAmount), 0), COALESCE(SUM(p.otherCharges), 0),
               COALESCE(SUM(p.returnAmount), 0), COALESCE(SUM(p.foreignNetAmount), 0),
               SUM(CASE WHEN p.currencyCode IS NOT NULL AND p.currencyCode <> 'MMK' THEN 1 ELSE 0 END)
        FROM Purchase p
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to IS NULL OR p.purchaseDate < :to)
        """)
    List<Object[]> analyticsTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(p.currencyCode, 'MMK'), COUNT(p), COALESCE(SUM(p.netAmount), 0)
        FROM Purchase p
        WHERE (p.status IS NULL OR p.status = org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
          AND (:from IS NULL OR p.purchaseDate >= :from)
          AND (:to IS NULL OR p.purchaseDate < :to)
        GROUP BY COALESCE(p.currencyCode, 'MMK')
        ORDER BY SUM(p.netAmount) DESC
        """)
    List<Object[]> spendByCurrency(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
