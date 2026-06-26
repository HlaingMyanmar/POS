package org.sspd.servicemgmt.saleoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.saleoptions.model.Sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Integer> {
    Optional<Sale> findTopByOrderByIdDesc();

    List<Sale> findTop10ByOrderByIdDesc();

    @Query("select coalesce(sum(s.netAmount), 0) from Sale s")
    BigDecimal sumTotalNetAmount();

    @Query("select coalesce(sum(s.netAmount), 0) from Sale s where s.saleDate >= :from")
    BigDecimal sumSalesFrom(@org.springframework.data.repository.query.Param("from") LocalDateTime from);

    @Query("select count(s) from Sale s where s.saleDate >= :from")
    long countSalesFrom(@org.springframework.data.repository.query.Param("from") LocalDateTime from);

    @Query("select coalesce(sum(s.dueAmount), 0) from Sale s where s.creditStatus = 'Overdue' and s.dueAmount > 0")
    BigDecimal sumOverdueAR();

    @Query("select count(s) from Sale s where s.creditStatus = 'Overdue' and s.dueAmount > 0")
    long countOverdueAR();

    @Query("select coalesce(sum(s.dueAmount), 0) from Sale s where s.dueAmount > 0")
    BigDecimal sumAllPendingAR();

    @Query("select count(s) from Sale s where s.dueAmount > 0")
    long countAllPendingAR();

    @Query("select coalesce(sum(s.dueAmount),0) from Sale s where s.customer.id = :customerId and (:excludeId is null or s.id <> :excludeId)")
    BigDecimal sumOutstandingDue(Integer customerId, Integer excludeId);

    @Query("""
        SELECT s FROM Sale s
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(s.saleCode) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(s.customer.name) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(s.staff.name) LIKE LOWER(CONCAT('%',:search,'%')))
          AND (:dateFrom IS NULL OR s.saleDate >= :dateFrom)
          AND (:dateTo   IS NULL OR s.saleDate <  :dateTo)
        """)
    Page<Sale> findBySearch(@Param("search") String search,
                            @Param("dateFrom") LocalDateTime dateFrom,
                            @Param("dateTo")   LocalDateTime dateTo,
                            Pageable pageable);

    List<Sale> findByDueAmountGreaterThan(BigDecimal amount);

    List<Sale> findByCreditStatusInAndDueAmountGreaterThan(java.util.Collection<org.sspd.servicemgmt.saleoptions.model.CreditStatus> statuses, BigDecimal amount);

    @Query("""
        SELECT s.staff.id, s.staff.name, s.staff.role,
               COUNT(s), COALESCE(SUM(s.netAmount), 0)
        FROM Sale s
        WHERE s.saleDate >= :from AND s.saleDate < :to
        GROUP BY s.staff.id, s.staff.name, s.staff.role
        """)
    List<Object[]> staffSaleStats(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT FUNCTION('YEAR', s.saleDate), FUNCTION('MONTH', s.saleDate),
               COUNT(s), COALESCE(SUM(s.netAmount), 0)
        FROM Sale s
        WHERE (:from IS NULL OR s.saleDate >= :from)
          AND (:to   IS NULL OR s.saleDate <  :to)
        GROUP BY FUNCTION('YEAR', s.saleDate), FUNCTION('MONTH', s.saleDate)
        ORDER BY FUNCTION('YEAR', s.saleDate) DESC, FUNCTION('MONTH', s.saleDate) DESC
        """)
    List<Object[]> monthlySalesSummaryRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT s.customer.id, s.customer.name, COUNT(s), COALESCE(SUM(s.netAmount), 0)
        FROM Sale s
        WHERE (:from IS NULL OR s.saleDate >= :from)
          AND (:to   IS NULL OR s.saleDate <  :to)
        GROUP BY s.customer.id, s.customer.name
        ORDER BY SUM(s.netAmount) DESC
        """)
    List<Object[]> salesByCustomer(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COUNT(s), COALESCE(SUM(s.netAmount), 0),
               COALESCE(SUM(s.discountAmount), 0), COALESCE(SUM(s.dueAmount), 0)
        FROM Sale s
        WHERE (:from IS NULL OR s.saleDate >= :from)
          AND (:to   IS NULL OR s.saleDate <  :to)
        """)
    List<Object[]> salesTotals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
