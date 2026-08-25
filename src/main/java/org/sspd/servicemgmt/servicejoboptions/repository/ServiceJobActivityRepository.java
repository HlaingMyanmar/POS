package org.sspd.servicemgmt.servicejoboptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobActivity;

import java.util.List;

public interface ServiceJobActivityRepository extends JpaRepository<ServiceJobActivity, Integer> {
    List<ServiceJobActivity> findByServiceJobIdOrderByOccurredAtAsc(Integer serviceJobId);
}
