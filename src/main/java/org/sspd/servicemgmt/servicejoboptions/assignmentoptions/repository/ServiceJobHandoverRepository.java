package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobHandover;

import java.util.List;
import java.util.Optional;

public interface ServiceJobHandoverRepository extends JpaRepository<ServiceJobHandover, Integer> {
    List<ServiceJobHandover> findAllByServiceJobIdOrderByRequestedAtDesc(Integer serviceJobId);
    boolean existsByFromAssignmentIdAndStatus(Integer assignmentId, HandoverStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from ServiceJobHandover h where h.id = :id and h.serviceJob.id = :jobId")
    Optional<ServiceJobHandover> findForUpdate(@Param("jobId") Integer jobId, @Param("id") Integer id);
}
