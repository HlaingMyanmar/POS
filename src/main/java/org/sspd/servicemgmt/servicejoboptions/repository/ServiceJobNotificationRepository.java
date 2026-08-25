package org.sspd.servicemgmt.servicejoboptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobNotification;

import java.util.List;

public interface ServiceJobNotificationRepository extends JpaRepository<ServiceJobNotification, Integer> {
    List<ServiceJobNotification> findByServiceJobIdOrderByNotifiedAtDesc(Integer serviceJobId);
}
