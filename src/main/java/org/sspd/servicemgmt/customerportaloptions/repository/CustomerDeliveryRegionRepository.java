package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryRegion;

import java.util.List;

public interface CustomerDeliveryRegionRepository extends JpaRepository<CustomerDeliveryRegion, Integer> {
    List<CustomerDeliveryRegion> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<CustomerDeliveryRegion> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);
}
