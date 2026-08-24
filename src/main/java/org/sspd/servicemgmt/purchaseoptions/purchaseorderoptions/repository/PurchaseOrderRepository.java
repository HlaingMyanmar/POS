package org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.purchaseoptions.purchaseorderoptions.model.PurchaseOrder;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Integer> {
    Optional<PurchaseOrder> findTopByOrderByIdDesc();

    @Query("SELECT o FROM PurchaseOrder o WHERE (:search IS NULL OR :search = '' OR LOWER(o.poCode) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(o.supplier.name) LIKE LOWER(CONCAT('%',:search,'%')) OR LOWER(o.staff.name) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<PurchaseOrder> findBySearch(@Param("search") String search, Pageable pageable);

    List<PurchaseOrder> findBySupplierIdAndStatusIn(Integer supplierId, List<String> statuses);
}
