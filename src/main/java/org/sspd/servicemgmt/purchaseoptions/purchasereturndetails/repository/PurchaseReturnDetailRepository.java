package org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturndetails.model.PurchaseReturnDetail;

import java.util.List;

@Repository
public interface PurchaseReturnDetailRepository extends JpaRepository<PurchaseReturnDetail, Integer> {
    List<PurchaseReturnDetail> findAllByPurchaseReturnId(Integer returnId);
}
