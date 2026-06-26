package org.sspd.servicemgmt.saleoptions.saledetails.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.saleoptions.saledetails.model.SaleDetail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleDetailRepository extends JpaRepository<SaleDetail, Integer> {
    List<SaleDetail> findAllBySaleId(Integer saleId);

    @Query("""
        SELECT d.product.id, d.product.name, d.product.productCode,
               SUM(d.qty), SUM(d.subtotal)
        FROM SaleDetail d
        WHERE (:from IS NULL OR d.sale.saleDate >= :from)
          AND (:to   IS NULL OR d.sale.saleDate <  :to)
          AND d.foc = false
        GROUP BY d.product.id, d.product.name, d.product.productCode
        ORDER BY SUM(d.qty) DESC
        """)
    List<Object[]> topProductsByQty(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT FUNCTION('YEAR', d.sale.saleDate), FUNCTION('MONTH', d.sale.saleDate),
               SUM(d.qty), SUM(d.subtotal)
        FROM SaleDetail d
        WHERE d.foc = false
        GROUP BY FUNCTION('YEAR', d.sale.saleDate), FUNCTION('MONTH', d.sale.saleDate)
        ORDER BY FUNCTION('YEAR', d.sale.saleDate) DESC, FUNCTION('MONTH', d.sale.saleDate) DESC
        """)
    List<Object[]> monthlySalesSummary();

    @Query("""
        SELECT COALESCE(SUM(d.subtotal - COALESCE(d.costPriceSnapshot, 0) * d.qty), 0)
        FROM SaleDetail d
        WHERE (:from IS NULL OR d.sale.saleDate >= :from)
          AND (:to   IS NULL OR d.sale.saleDate <= :to)
          AND d.foc = false
        """)
    BigDecimal saleProfitInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
