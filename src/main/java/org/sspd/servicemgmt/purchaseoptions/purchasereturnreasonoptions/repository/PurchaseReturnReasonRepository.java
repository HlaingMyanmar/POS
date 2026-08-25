package org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.purchaseoptions.purchasereturnreasonoptions.model.PurchaseReturnReason;

import java.util.List;
import java.util.Optional;

public interface PurchaseReturnReasonRepository extends JpaRepository<PurchaseReturnReason, Integer> {
    List<PurchaseReturnReason> findAllByOrderByNameAsc();
    List<PurchaseReturnReason> findByActiveTrueOrderByNameAsc();
    Optional<PurchaseReturnReason> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);
}
