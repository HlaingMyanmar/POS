package org.sspd.servicemgmt.technicianvisitoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitEvent;

import java.util.List;

public interface TechnicianVisitEventRepository extends JpaRepository<TechnicianVisitEvent, Long> {
    List<TechnicianVisitEvent> findByVisitIdOrderByOccurredAtAscIdAsc(Long visitId);
}
