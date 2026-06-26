package org.sspd.servicemgmt.purchaseoptions.purchasedetails.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.purchasedetails.model.PurchaseDetailWarranty;

import java.util.Optional;

@Repository
public interface PurchaseDetailWarrantyRepository extends JpaRepository<PurchaseDetailWarranty, Integer> {
    Optional<PurchaseDetailWarranty> findTopBySerialNumberOrderByIdDesc(String serialNumber);
}
