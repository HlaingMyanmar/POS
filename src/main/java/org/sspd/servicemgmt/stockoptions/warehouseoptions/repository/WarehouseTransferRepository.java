package org.sspd.servicemgmt.stockoptions.warehouseoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.model.WarehouseTransfer;

import java.util.List;

public interface WarehouseTransferRepository extends JpaRepository<WarehouseTransfer, Integer> {
    List<WarehouseTransfer> findAllByOrderByIdDesc();
}
