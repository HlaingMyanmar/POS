package org.sspd.servicemgmt.customerportaloptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.customerportaloptions.model.CustomerDeliveryWard;

import java.util.List;
import java.util.Optional;

public interface CustomerDeliveryWardRepository extends JpaRepository<CustomerDeliveryWard, Integer> {

    List<CustomerDeliveryWard> findByActiveTrueAndTownship_ActiveTrueAndTownship_Region_ActiveTrueOrderByTownship_Region_SortOrderAscTownship_SortOrderAscSortOrderAscNameAsc();

    List<CustomerDeliveryWard> findAllByOrderByTownship_Region_SortOrderAscTownship_SortOrderAscSortOrderAscNameAsc();

    List<CustomerDeliveryWard> findByTownship_IdOrderBySortOrderAscNameAsc(Integer townshipId);

    boolean existsByTownship_IdAndNameIgnoreCase(Integer townshipId, String name);

    boolean existsByTownship_IdAndNameIgnoreCaseAndIdNot(Integer townshipId, String name, Integer id);

    @Query("select w from CustomerDeliveryWard w join fetch w.township t join fetch t.region where w.id = :id")
    Optional<CustomerDeliveryWard> findWithTownshipAndRegionById(@Param("id") Integer id);
}
