package org.sspd.servicemgmt.servicejoboptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobLine;

public interface ServiceJobLineRepository extends JpaRepository<ServiceJobLine, Integer> {
    boolean existsByServiceItem_Id(Integer serviceItemId);
}
