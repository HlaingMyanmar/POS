package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.GoodsReceipt;
import java.time.LocalDateTime;
import java.util.List;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Integer> {
    List<GoodsReceipt> findByPurchaseOrderIdOrderByIdDesc(Integer purchaseOrderId);
    List<GoodsReceipt> findByPurchaseIdOrderByIdDesc(Integer purchaseId);

    @Query("""
        SELECT COUNT(g), SUM(CASE WHEN g.matchStatus = 'VARIANCE' THEN 1 ELSE 0 END)
        FROM GoodsReceipt g
        WHERE (:from IS NULL OR g.receivedAt >= :from)
          AND (:to IS NULL OR g.receivedAt < :to)
        """)
    List<Object[]> matchCounts(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
