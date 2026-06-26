package org.sspd.servicemgmt.purchaseoptions.purchasedetails.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetail;

import java.util.List;

@Repository
public interface PurchaseDetailRepository extends JpaRepository<PurchaseDetail, Integer> {
    List<PurchaseDetail> findByPurchaseId(Integer purchaseId);
}