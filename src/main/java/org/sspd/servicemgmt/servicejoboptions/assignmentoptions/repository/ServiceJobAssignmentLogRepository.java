package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobAssignmentLog;

import java.util.List;

public interface ServiceJobAssignmentLogRepository extends JpaRepository<ServiceJobAssignmentLog, Integer> {
    List<ServiceJobAssignmentLog> findAllByAssignmentIdOrderByOccurredAtAsc(Integer assignmentId);
}
