package org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnoptions.model.PurchaseReturn;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Integer> {
    Optional<PurchaseReturn> findByReturnNo(String returnNo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PurchaseReturn r WHERE r.id = :id")
    Optional<PurchaseReturn> findByIdForUpdate(@Param("id") Integer id);
    Optional<PurchaseReturn> findTopByOrderByIdDesc();
    List<PurchaseReturn> findByPurchaseId(Integer purchaseId);

    @Query("SELECT r FROM PurchaseReturn r WHERE (:search IS NULL OR :search = '' OR LOWER(r.returnNo) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.reason) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.purchase.supplier.name) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<PurchaseReturn> findBySearch(@Param("search") String search, Pageable pageable);
    @Query("SELECT r FROM PurchaseReturn r WHERE (:search IS NULL OR :search='' OR LOWER(r.returnNo) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.reason) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.purchase.supplier.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.trackingNo) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(r.rmaNumber) LIKE LOWER(CONCAT('%',:search,'%'))) AND (:from IS NULL OR r.returnDate>=:from) AND (:to IS NULL OR r.returnDate<=:to) AND (:supplierId IS NULL OR r.purchase.supplier.id=:supplierId) AND (:purchaseId IS NULL OR r.purchase.id=:purchaseId) AND (:status IS NULL OR :status='' OR r.status=:status) AND (:settlementType IS NULL OR :settlementType='' OR r.settlementType=:settlementType) AND (:resolutionType IS NULL OR :resolutionType='' OR r.resolutionType=:resolutionType)")
    Page<PurchaseReturn> findFiltered(@Param("search") String search,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to,@Param("supplierId") Integer supplierId,@Param("purchaseId") Integer purchaseId,@Param("status") String status,@Param("settlementType") String settlementType,@Param("resolutionType") String resolutionType,Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(r.totalReturnAmount), 0)
        FROM PurchaseReturn r
        WHERE (:from IS NULL OR r.returnDate >= :from)
          AND (:to   IS NULL OR r.returnDate <= :to)
          AND r.status = 'SETTLED'
        """)
    BigDecimal sumInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
