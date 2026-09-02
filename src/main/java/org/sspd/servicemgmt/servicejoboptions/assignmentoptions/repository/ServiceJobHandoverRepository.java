package org.sspd.servicemgmt.servicejoboptions.assignmentoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.ServiceJobHandover;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceJobHandoverRepository extends JpaRepository<ServiceJobHandover, Integer> {
    List<ServiceJobHandover> findAllByServiceJobIdOrderByRequestedAtDesc(Integer serviceJobId);
    boolean existsByFromAssignmentIdAndStatus(Integer assignmentId, HandoverStatus status);
    boolean existsByServiceJobIdAndToStaffIdAndStatus(Integer serviceJobId, Integer toStaffId, HandoverStatus status);

    @Query("""
        SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END
        FROM ServiceJobHandover h
        WHERE h.serviceJob.id = :serviceJobId
          AND h.fromAssignment.staff.id = :fromStaffId
          AND h.status = :status
        """)
    boolean existsByServiceJobIdAndFromStaffIdAndStatus(
            @Param("serviceJobId") Integer serviceJobId,
            @Param("fromStaffId") Integer fromStaffId,
            @Param("status") HandoverStatus status);

    @Query("""
        SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END
        FROM ServiceJobHandover h
        WHERE h.serviceJob.id = :serviceJobId
          AND h.fromAssignment.staff.id = :fromStaffId
        """)
    boolean existsByServiceJobIdAndFromStaffId(
            @Param("serviceJobId") Integer serviceJobId,
            @Param("fromStaffId") Integer fromStaffId);

    @Query("""
        SELECT h FROM ServiceJobHandover h
        JOIN FETCH h.fromAssignment fa
        JOIN FETCH fa.staff
        JOIN FETCH h.toStaff
        JOIN FETCH h.serviceJob
        WHERE fa.staff.id = :staffId
          AND h.status IN :statuses
        ORDER BY h.requestedAt DESC
        """)
    List<ServiceJobHandover> findAllByFromStaffIdAndStatusInOrderByRequestedAtDesc(
            @Param("staffId") Integer staffId,
            @Param("statuses") Collection<HandoverStatus> statuses);
    Optional<ServiceJobHandover> findFirstByServiceJobIdAndToStaffIdAndStatus(
            Integer serviceJobId, Integer toStaffId, HandoverStatus status);
    List<ServiceJobHandover> findAllByToStaffIdAndStatusOrderByRequestedAtDesc(Integer toStaffId, HandoverStatus status);

    @Query("""
        SELECT h FROM ServiceJobHandover h
        JOIN FETCH h.fromAssignment fa
        JOIN FETCH fa.staff
        WHERE h.toStaff.id = :staffId
          AND h.status = :status
          AND h.serviceJob.id IN :jobIds
        """)
    List<ServiceJobHandover> findAllByToStaffIdAndStatusAndServiceJobIdIn(
            @Param("staffId") Integer staffId,
            @Param("status") HandoverStatus status,
            @Param("jobIds") Collection<Integer> jobIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from ServiceJobHandover h where h.id = :id and h.serviceJob.id = :jobId")
    Optional<ServiceJobHandover> findForUpdate(@Param("jobId") Integer jobId, @Param("id") Integer id);
}
