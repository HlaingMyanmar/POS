package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryTownship;

import java.util.List;

public interface CustomerDeliveryTownshipRepository extends JpaRepository<CustomerDeliveryTownship, Integer> {
    List<CustomerDeliveryTownship> findByActiveTrueAndRegion_ActiveTrueOrderByRegion_SortOrderAscSortOrderAscNameAsc();

    List<CustomerDeliveryTownship> findAllByOrderByRegion_SortOrderAscSortOrderAscNameAsc();

    List<CustomerDeliveryTownship> findByRegion_IdOrderBySortOrderAscNameAsc(Integer regionId);

    boolean existsByRegion_IdAndNameIgnoreCase(Integer regionId, String name);

    boolean existsByRegion_IdAndNameIgnoreCaseAndIdNot(Integer regionId, String name, Integer id);
}
