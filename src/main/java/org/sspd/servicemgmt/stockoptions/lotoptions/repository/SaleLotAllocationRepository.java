package org.sspd.servicemgmt.stockoptions.lotoptions.repository;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import org.sspd.servicemgmt.stockoptions.lotoptions.model.SaleLotAllocation;import java.util.*;
public interface SaleLotAllocationRepository extends JpaRepository<SaleLotAllocation,Integer>{
 List<SaleLotAllocation> findBySaleDetailSaleId(Integer saleId);
 @Query("select a from SaleLotAllocation a where a.saleDetail.sale.id=:saleId and a.saleDetail.product.id=:productId and a.returnedQty<a.allocatedQty order by a.id")
 List<SaleLotAllocation> findReturnable(@Param("saleId")Integer saleId,@Param("productId")Integer productId);
}
