package org.sspd.servicemgmt.stockoptions.lotoptions.repository;
import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import org.sspd.servicemgmt.stockoptions.lotoptions.model.StockLot;import java.time.LocalDate;import java.util.*;
public interface StockLotRepository extends JpaRepository<StockLot,Integer>{
 Optional<StockLot> findByPurchaseDetailId(Integer id);List<StockLot> findByPurchaseDetailPurchaseId(Integer purchaseId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from StockLot l where l.product.id=:productId and l.remainingQty>0 and l.status='AVAILABLE' and (l.expiryDate is null or l.expiryDate>=:today) order by case when l.expiryDate is null then 1 else 0 end,l.expiryDate,l.receivedAt,l.id")
 List<StockLot> findSellableFefo(@Param("productId")Integer productId,@Param("today")LocalDate today);
 @Query("select coalesce(sum(l.remainingQty),0) from StockLot l where l.product.id=:productId and l.status in ('AVAILABLE','DEPLETED')")
 Long sumTrackedRemaining(@Param("productId")Integer productId);
 @Query("select l from StockLot l where l.remainingQty>0 and l.expiryDate is not null and l.expiryDate<=:until and l.status='AVAILABLE' order by l.expiryDate,l.product.name")
 List<StockLot> findExpiring(@Param("until")LocalDate until);
 List<StockLot> findByRemainingQtyGreaterThanAndStatusIn(Integer remainingQty, java.util.Collection<String> statuses);
}
