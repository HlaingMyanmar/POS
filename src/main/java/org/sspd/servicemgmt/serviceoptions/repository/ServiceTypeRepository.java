package org.sspd.servicemgmt.serviceoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.serviceoptions.model.ServiceType;

import java.util.List;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Integer> {
    List<ServiceType> findByIsActiveTrue();
}
