package org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.purchaseoptions.supplierpaymentoptions.model.SupplierCreditApplication;

import java.util.List;
import java.util.Optional;

public interface SupplierCreditApplicationRepository extends JpaRepository<SupplierCreditApplication, Integer> {
    List<SupplierCreditApplication> findBySupplierIdOrderByIdDesc(Integer supplierId);
    List<SupplierCreditApplication> findByTargetPurchaseIdOrderByIdDesc(Integer purchaseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SupplierCreditApplication a where a.id = :id")
    Optional<SupplierCreditApplication> findByIdForUpdate(@Param("id") Integer id);
}
