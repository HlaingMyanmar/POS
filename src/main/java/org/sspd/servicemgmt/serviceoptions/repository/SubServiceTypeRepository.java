package org.sspd.servicemgmt.serviceoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.serviceoptions.model.SubServiceType;

import java.util.List;

@Repository
public interface SubServiceTypeRepository extends JpaRepository<SubServiceType, Integer> {
    List<SubServiceType> findByServiceTypeId(Integer serviceTypeId);
    List<SubServiceType> findByServiceTypeIdAndIsActiveTrue(Integer serviceTypeId);
}
