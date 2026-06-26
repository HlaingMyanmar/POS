package org.sspd.servicemgmt.serviceoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.serviceoptions.model.ServiceItem;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceItemRepository extends JpaRepository<ServiceItem, Integer> {
    List<ServiceItem> findByIsActiveTrue();
    List<ServiceItem> findByServiceTypeId(Integer serviceTypeId);
    Optional<ServiceItem> findTopByOrderByIdDesc();
    boolean existsByCode(String code);
    boolean existsByItem(String item);
}
