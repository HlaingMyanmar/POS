package org.sspd.servicemgmt.stockoptions.manufacturingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.enums.ManufacturingStatus;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.model.ManufacturingOrder;

import java.util.List;

public interface ManufacturingOrderRepository extends JpaRepository<ManufacturingOrder, Integer> {

    @Query("SELECT COALESCE(MAX(o.id), 0) FROM ManufacturingOrder o")
    Integer findMaxId();

    List<ManufacturingOrder> findAllByOrderByCreatedAtDesc();

    boolean existsByFinishedProductIdAndStatus(Integer finishedProductId, ManufacturingStatus status);
}
