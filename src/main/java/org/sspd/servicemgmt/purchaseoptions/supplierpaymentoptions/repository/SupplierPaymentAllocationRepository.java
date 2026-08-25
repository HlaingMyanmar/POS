package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierPaymentAllocation;
import java.util.List;

public interface SupplierPaymentAllocationRepository extends JpaRepository<SupplierPaymentAllocation, Integer> {
    List<SupplierPaymentAllocation> findByPurchaseId(Integer purchaseId);
}
