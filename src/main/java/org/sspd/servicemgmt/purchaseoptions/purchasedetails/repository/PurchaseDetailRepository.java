package org.sspd.servicemgmt.purchaseoptions.purchasedetails.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface PurchaseDetailRepository extends JpaRepository<PurchaseDetail, Integer> {
    List<PurchaseDetail> findByPurchaseId(Integer purchaseId);
    @Query("""
      select coalesce(sum(d.subtotal),0) from PurchaseDetail d
      where (d.purchase.status is null or d.purchase.status=org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
        and d.purchase.purchaseDate>=:from and d.purchase.purchaseDate<:to
        and (:categoryId is null or d.product.category.id=:categoryId)
      """)
    BigDecimal sumConfirmedSpend(@Param("from") LocalDateTime from,@Param("to") LocalDateTime to,@Param("categoryId") Integer categoryId);

    @Query("""
      select coalesce(d.product.category.name, 'Uncategorized'), count(distinct d.purchase.id), coalesce(sum(d.subtotal),0)
      from PurchaseDetail d
      where (d.purchase.status is null or d.purchase.status=org.sspd.servicemgmt.purchaseoptions.model.PurchaseStatus.CONFIRMED)
        and (:from is null or d.purchase.purchaseDate>=:from)
        and (:to is null or d.purchase.purchaseDate<:to)
      group by d.product.category.name
      order by coalesce(sum(d.subtotal),0) desc
      """)
    List<Object[]> spendByCategory(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
