package org.sspd.servicemgmt.saleoptions.salereturndetails.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.saleoptions.salereturndetails.model.SaleReturnDetail;
import org.sspd.servicemgmt.saleoptions.salereturnoptions.model.SaleReturn;

import java.util.List;

@Repository
public interface SaleReturnDetailRepository extends JpaRepository<SaleReturnDetail, Integer> {
    List<SaleReturnDetail> findAllBySaleReturnId(Integer returnId);
    List<SaleReturnDetail> findAllBySaleReturnIn(List<SaleReturn> returns);
}
