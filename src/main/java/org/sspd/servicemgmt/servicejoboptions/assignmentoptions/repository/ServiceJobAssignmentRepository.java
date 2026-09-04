package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentRole;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobAssignment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceJobAssignmentRepository extends JpaRepository<ServiceJobAssignment, Integer> {
    List<ServiceJobAssignment> findAllByServiceJobIdOrderByAssignedAtAscIdAsc(Integer serviceJobId);
    List<ServiceJobAssignment> findAllByServiceJobIdAndStatusInOrderByAssignedAtAsc(
            Integer serviceJobId, Collection<AssignmentStatus> statuses);
    List<ServiceJobAssignment> findAllByStaffIdAndStatusInOrderByAssignedAtDesc(
            Integer staffId, Collection<AssignmentStatus> statuses);
    List<ServiceJobAssignment> findAllByStaffIdAndServiceJobIdInAndStatusIn(
            Integer staffId, Collection<Integer> serviceJobIds, Collection<AssignmentStatus> statuses);
    boolean existsByServiceJobIdAndStaffIdAndStatusIn(
            Integer serviceJobId, Integer staffId, Collection<AssignmentStatus> statuses);
    boolean existsByServiceJobIdAndRoleAndStatusIn(
            Integer serviceJobId, AssignmentRole role, Collection<AssignmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ServiceJobAssignment a where a.id = :id and a.serviceJob.id = :jobId")
    Optional<ServiceJobAssignment> findForUpdate(@Param("jobId") Integer jobId, @Param("id") Integer id);
}
