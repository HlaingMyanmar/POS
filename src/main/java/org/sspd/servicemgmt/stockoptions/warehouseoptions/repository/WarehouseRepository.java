package org.sspd.servicemgmt.stockoptions.warehouseoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.stockoptions.warehouseoptions.model.Warehouse;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Integer> {
    List<Warehouse> findAllByOrderByNameAsc();
    List<Warehouse> findByActiveTrueOrderByNameAsc();
    Optional<Warehouse> findByCodeIgnoreCase(String code);
    Optional<Warehouse> findByNameIgnoreCase(String name);
    boolean existsByCodeIgnoreCase(String code);
}
