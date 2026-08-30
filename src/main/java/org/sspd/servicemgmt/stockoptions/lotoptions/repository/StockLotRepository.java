package org.sspd.servicemgmt.stockoptions.lotoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.stockoptions.lotoptions.model.StockLot;

import java.time.LocalDate;
import java.util.*;

public interface StockLotRepository extends JpaRepository<StockLot, Integer> {
    Optional<StockLot> findByPurchaseDetailId(Integer id);
    List<StockLot> findByPurchaseDetailPurchaseId(Integer purchaseId);
    List<StockLot> findByProductIdOrderByExpiryDateAscReceivedAtAscIdAsc(Integer productId);
    boolean existsByProductIdAndSourceTypeAndBatchNumber(Integer productId, String sourceType, String batchNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from StockLot l where l.product.id=:productId and l.remainingQty>0 and l.status='AVAILABLE' and (l.expiryDate is null or l.expiryDate>=:today) order by case when l.expiryDate is null then 1 else 0 end,l.expiryDate,l.receivedAt,l.id")
    List<StockLot> findSellableFefo(@Param("productId") Integer productId, @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from StockLot l where l.product.id=:productId and l.remainingQty>0 and l.status='AVAILABLE' and (l.expiryDate is null or l.expiryDate>=:today) and (:warehouseId is null or l.warehouse.id=:warehouseId) order by case when l.expiryDate is null then 1 else 0 end,l.expiryDate,l.receivedAt,l.id")
    List<StockLot> findSellableFefoInWarehouse(@Param("productId") Integer productId, @Param("warehouseId") Integer warehouseId, @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from StockLot l where l.product.id=:productId and l.remainingQty>0 and l.status='AVAILABLE' order by case when l.expiryDate is null then 1 when l.expiryDate>=:today then 0 else 2 end,l.expiryDate,l.receivedAt,l.id")
    List<StockLot> findAllocatableFefo(@Param("productId") Integer productId, @Param("today") LocalDate today);

    @Query("select coalesce(sum(l.remainingQty),0) from StockLot l where l.product.id=:productId and l.warehouse.id=:warehouseId and l.status='AVAILABLE' and l.remainingQty>0 and (l.expiryDate is null or l.expiryDate>=:today)")
    Long sumSellableInWarehouse(@Param("productId") Integer productId, @Param("warehouseId") Integer warehouseId, @Param("today") LocalDate today);

    @Query("select coalesce(sum(l.remainingQty),0) from StockLot l where l.product.id=:productId and l.status in ('AVAILABLE','DEPLETED')")
    Long sumTrackedRemaining(@Param("productId") Integer productId);

    @Query("select coalesce(sum(l.remainingQty),0) from StockLot l where l.product.id=:productId and l.warehouse.id=:warehouseId and l.status in ('AVAILABLE','DEPLETED')")
    Long sumRemainingInWarehouse(@Param("productId") Integer productId, @Param("warehouseId") Integer warehouseId);

    @Query("select l from StockLot l where l.remainingQty>0 and l.expiryDate is not null and l.expiryDate<=:until and l.status='AVAILABLE' order by l.expiryDate,l.product.name")
    List<StockLot> findExpiring(@Param("until") LocalDate until);

    List<StockLot> findByRemainingQtyGreaterThanAndStatusIn(Integer remainingQty, Collection<String> statuses);

    List<StockLot> findByProductIdAndStatusIn(Integer productId, Collection<String> statuses);
}
