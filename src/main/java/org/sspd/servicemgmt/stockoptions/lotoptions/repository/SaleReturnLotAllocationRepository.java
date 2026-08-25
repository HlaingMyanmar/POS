package org.sspd.servicemgmt.stockoptions.lotoptions.repository;
import org.springframework.data.jpa.repository.JpaRepository;import org.sspd.servicemgmt.stockoptions.lotoptions.model.SaleReturnLotAllocation;import java.util.*;
public interface SaleReturnLotAllocationRepository extends JpaRepository<SaleReturnLotAllocation,Integer>{List<SaleReturnLotAllocation> findBySaleReturnDetailSaleReturnId(Integer returnId);}
