package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication;
import java.util.List;

public interface SupplierCreditApplicationRepository extends JpaRepository<SupplierCreditApplication, Integer> {
    List<SupplierCreditApplication> findBySupplierIdOrderByIdDesc(Integer supplierId);
    List<SupplierCreditApplication> findByTargetPurchaseIdOrderByIdDesc(Integer purchaseId);
}
