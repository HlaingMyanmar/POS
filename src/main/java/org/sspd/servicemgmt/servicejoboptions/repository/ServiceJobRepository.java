package org.sspd.servicemgmt.servicejoboptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.AssignmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceJobRepository extends JpaRepository<ServiceJob, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ServiceJob j where j.id = :id")
    Optional<ServiceJob> findByIdForUpdate(@Param("id") Integer id);

    @Query("""
        SELECT CASE WHEN COUNT(j) > 0 THEN true ELSE false END
        FROM ServiceJob j
        WHERE LOWER(j.serialNo) = LOWER(:serial)
          AND j.status NOT IN (org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.DELIVERED,
                               org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.CANCELLED)
          AND COALESCE(j.voided, false) = false
          AND (:excludeJobId IS NULL OR j.id <> :excludeJobId)
        """)
    boolean existsOpenDeviceSerial(@Param("serial") String serial, @Param("excludeJobId") Integer excludeJobId);

    @Query("""
        SELECT j FROM ServiceJob j
        WHERE j.estimatedCompletion IS NOT NULL
          AND j.estimatedCompletion < :now
          AND j.status NOT IN (
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.COMPLETED,
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.DELIVERED,
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.CANCELLED)
        """)
    List<ServiceJob> findOverdue(@Param("now") LocalDateTime now);

    List<ServiceJob> findByStatus(ServiceJobStatus status);
    List<ServiceJob> findByStatusAndPaymentStatusIsNullOrderByReceivedDateDesc(ServiceJobStatus status);
    List<ServiceJob> findByCustomerId(Integer customerId);
    List<ServiceJob> findByAssignedStaffId(Integer staffId);
    long countByStatus(ServiceJobStatus status);

    @Query("select coalesce(sum(j.netAmount), 0) from ServiceJob j where j.receivedDate >= :from and j.receivedDate < :to")
    BigDecimal sumNetAmountInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(j) from ServiceJob j where j.receivedDate >= :from and j.receivedDate < :to")
    long countInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(j) from ServiceJob j where j.dueAmount > 0 and j.status <> org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.CANCELLED")
    long countPendingPayment();

    @Query("select count(j) from ServiceJob j where j.status = org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.COMPLETED")
    long countPendingDelivery();

    @Query("select count(j) from ServiceJob j where j.rework = true and j.receivedDate >= :from and j.receivedDate < :to")
    long countReworkInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<ServiceJob> findAllByBookingIdOrderByIdAsc(Integer bookingId);
    boolean existsByBookingIdAndServiceMode(Integer bookingId, org.sspd.servicemgmt.servicejoboptions.model.ServiceMode serviceMode);

    @Query("""
        SELECT j FROM ServiceJob j
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(j.jobNo) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(j.customer.name) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(j.itemName) LIKE LOWER(CONCAT('%',:search,'%')))
          AND (:dateFrom IS NULL OR j.receivedDate >= :dateFrom)
          AND (:dateTo   IS NULL OR j.receivedDate <  :dateTo)
        ORDER BY CASE
                   WHEN j.priority = 'URGENT' THEN 0
                   WHEN j.priority = 'HIGH' THEN 1
                   WHEN j.priority = 'NORMAL' THEN 2
                   WHEN j.priority = 'LOW' THEN 3
                   ELSE 4
                 END ASC,
                 j.receivedDate ASC,
                 j.id ASC
        """)
    org.springframework.data.domain.Page<ServiceJob> findBySearchAndDate(
            @Param("search")   String search,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo")   LocalDateTime dateTo,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT DISTINCT j FROM ServiceJob j
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(j.jobNo) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(j.customer.name) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(j.itemName) LIKE LOWER(CONCAT('%',:search,'%')))
          AND (:dateFrom IS NULL OR j.receivedDate >= :dateFrom)
          AND (:dateTo   IS NULL OR j.receivedDate <  :dateTo)
          AND (
               j.assignedStaff.id = :staffId
            OR j.helperStaff.id = :staffId
            OR EXISTS (
                 SELECT 1 FROM ServiceJobAssignment a
                 WHERE a.serviceJob.id = j.id
                   AND a.staff.id = :staffId
                   AND a.status IN :assignmentStatuses
               )
            OR EXISTS (
                 SELECT 1 FROM ServiceJobHandover h
                 WHERE h.serviceJob.id = j.id
                   AND h.toStaff.id = :staffId
                   AND h.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
               )
          )
          AND NOT EXISTS (
               SELECT 1 FROM ServiceJobHandover hOut
               WHERE hOut.serviceJob.id = j.id
                 AND hOut.fromAssignment.staff.id = :staffId
                 AND hOut.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
          )
        ORDER BY CASE
                   WHEN j.priority = 'URGENT' THEN 0
                   WHEN j.priority = 'HIGH' THEN 1
                   WHEN j.priority = 'NORMAL' THEN 2
                   WHEN j.priority = 'LOW' THEN 3
                   ELSE 4
                 END ASC,
                 j.receivedDate ASC,
                 j.id ASC
        """)
    Page<ServiceJob> findBySearchAndDateForStaff(
            @Param("search") String search,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("staffId") Integer staffId,
            @Param("assignmentStatuses") Collection<AssignmentStatus> assignmentStatuses,
            Pageable pageable);

    @Query("""
        SELECT DISTINCT j FROM ServiceJob j
        WHERE j.status = :status
          AND (
               j.assignedStaff.id = :staffId
            OR j.helperStaff.id = :staffId
            OR EXISTS (
                 SELECT 1 FROM ServiceJobAssignment a
                 WHERE a.serviceJob.id = j.id
                   AND a.staff.id = :staffId
                   AND a.status IN :assignmentStatuses
               )
            OR EXISTS (
                 SELECT 1 FROM ServiceJobHandover h
                 WHERE h.serviceJob.id = j.id
                   AND h.toStaff.id = :staffId
                   AND h.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
               )
          )
          AND NOT EXISTS (
               SELECT 1 FROM ServiceJobHandover hOut
               WHERE hOut.serviceJob.id = j.id
                 AND hOut.fromAssignment.staff.id = :staffId
                 AND hOut.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
          )
        ORDER BY j.id DESC
        """)
    List<ServiceJob> findByStatusForStaff(
            @Param("status") ServiceJobStatus status,
            @Param("staffId") Integer staffId,
            @Param("assignmentStatuses") Collection<AssignmentStatus> assignmentStatuses);

    @Query("""
        SELECT DISTINCT j FROM ServiceJob j
        WHERE j.status = org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.COMPLETED
          AND j.paymentStatus IS NULL
          AND (
               j.assignedStaff.id = :staffId
            OR j.helperStaff.id = :staffId
            OR EXISTS (
                 SELECT 1 FROM ServiceJobAssignment a
                 WHERE a.serviceJob.id = j.id
                   AND a.staff.id = :staffId
                   AND a.status IN :assignmentStatuses
               )
            OR EXISTS (
                 SELECT 1 FROM ServiceJobHandover h
                 WHERE h.serviceJob.id = j.id
                   AND h.toStaff.id = :staffId
                   AND h.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
               )
          )
          AND NOT EXISTS (
               SELECT 1 FROM ServiceJobHandover hOut
               WHERE hOut.serviceJob.id = j.id
                 AND hOut.fromAssignment.staff.id = :staffId
                 AND hOut.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
          )
        ORDER BY j.receivedDate DESC
        """)
    List<ServiceJob> findUnpaidForStaff(
            @Param("staffId") Integer staffId,
            @Param("assignmentStatuses") Collection<AssignmentStatus> assignmentStatuses);

    @Query("""
        SELECT DISTINCT j FROM ServiceJob j
        WHERE j.estimatedCompletion IS NOT NULL
          AND j.estimatedCompletion < :now
          AND j.status NOT IN (
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.COMPLETED,
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.DELIVERED,
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.CANCELLED)
          AND (
               j.assignedStaff.id = :staffId
            OR j.helperStaff.id = :staffId
            OR EXISTS (
                 SELECT 1 FROM ServiceJobAssignment a
                 WHERE a.serviceJob.id = j.id
                   AND a.staff.id = :staffId
                   AND a.status IN :assignmentStatuses
               )
            OR EXISTS (
                 SELECT 1 FROM ServiceJobHandover h
                 WHERE h.serviceJob.id = j.id
                   AND h.toStaff.id = :staffId
                   AND h.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
               )
          )
          AND NOT EXISTS (
               SELECT 1 FROM ServiceJobHandover hOut
               WHERE hOut.serviceJob.id = j.id
                 AND hOut.fromAssignment.staff.id = :staffId
                 AND hOut.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
          )
        ORDER BY j.estimatedCompletion ASC
        """)
    List<ServiceJob> findOverdueForStaff(
            @Param("now") LocalDateTime now,
            @Param("staffId") Integer staffId,
            @Param("assignmentStatuses") Collection<AssignmentStatus> assignmentStatuses);

    @Query("""
        SELECT COUNT(DISTINCT j) FROM ServiceJob j
        WHERE j.status = :status
          AND (
               j.assignedStaff.id = :staffId
            OR j.helperStaff.id = :staffId
            OR EXISTS (
                 SELECT 1 FROM ServiceJobAssignment a
                 WHERE a.serviceJob.id = j.id
                   AND a.staff.id = :staffId
                   AND a.status IN :assignmentStatuses
               )
            OR EXISTS (
                 SELECT 1 FROM ServiceJobHandover h
                 WHERE h.serviceJob.id = j.id
                   AND h.toStaff.id = :staffId
                   AND h.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
               )
          )
          AND NOT EXISTS (
               SELECT 1 FROM ServiceJobHandover hOut
               WHERE hOut.serviceJob.id = j.id
                 AND hOut.fromAssignment.staff.id = :staffId
                 AND hOut.status = org.sspd.servicemgmt.servicejoboptions.assignmentoptions.model.HandoverStatus.PENDING
          )
        """)
    long countByStatusForStaff(
            @Param("status") ServiceJobStatus status,
            @Param("staffId") Integer staffId,
            @Param("assignmentStatuses") Collection<AssignmentStatus> assignmentStatuses);

    @Query("""
        SELECT sj.assignedStaff.id, sj.assignedStaff.name, sj.assignedStaff.role,
               COUNT(sj),
               SUM(CASE WHEN sj.status IN ('COMPLETED','DELIVERED') THEN 1 ELSE 0 END),
               COALESCE(SUM(sj.netAmount), 0),
               SUM(CASE WHEN sj.status = 'CANCELLED' THEN 1 ELSE 0 END),
               SUM(CASE WHEN sj.rework = true THEN 1 ELSE 0 END),
               SUM(CASE WHEN sj.status = 'IN_PROGRESS' THEN 1 ELSE 0 END)
        FROM ServiceJob sj
        WHERE sj.assignedStaff IS NOT NULL
          AND sj.receivedDate >= :from AND sj.receivedDate < :to
        GROUP BY sj.assignedStaff.id, sj.assignedStaff.name, sj.assignedStaff.role
        """)
    List<Object[]> staffServiceStats(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT CAST(sj.status AS string), COUNT(sj)
        FROM ServiceJob sj
        WHERE (:from IS NULL OR sj.receivedDate >= :from)
          AND (:to   IS NULL OR sj.receivedDate <  :to)
        GROUP BY sj.status
        """)
    List<Object[]> countByStatusInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT FUNCTION('YEAR', sj.receivedDate), FUNCTION('MONTH', sj.receivedDate),
               COUNT(sj), COALESCE(SUM(sj.netAmount), 0)
        FROM ServiceJob sj
        WHERE (:from IS NULL OR sj.receivedDate >= :from)
          AND (:to   IS NULL OR sj.receivedDate <  :to)
        GROUP BY FUNCTION('YEAR', sj.receivedDate), FUNCTION('MONTH', sj.receivedDate)
        ORDER BY FUNCTION('YEAR', sj.receivedDate) DESC, FUNCTION('MONTH', sj.receivedDate) DESC
        """)
    List<Object[]> monthlyJobSummary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(sj.netAmount), 0)
        FROM ServiceJob sj
        WHERE (:from IS NULL OR sj.receivedDate >= :from)
          AND (:to   IS NULL OR sj.receivedDate <= :to)
        """)
    BigDecimal sumNetAmountInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(sj.laborNetAmount), 0)
        FROM ServiceJob sj
        WHERE (:from IS NULL OR sj.receivedDate >= :from)
          AND (:to   IS NULL OR sj.receivedDate <= :to)
        """)
    BigDecimal sumLaborNetInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(sj.partsNetAmount), 0)
        FROM ServiceJob sj
        WHERE (:from IS NULL OR sj.receivedDate >= :from)
          AND (:to   IS NULL OR sj.receivedDate <= :to)
        """)
    BigDecimal sumPartsNetInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<ServiceJob> findByDueAmountGreaterThan(BigDecimal amount);

    @Query("select coalesce(sum(j.dueAmount),0) from ServiceJob j where j.customer.id = :customerId and (:excludeId is null or j.id <> :excludeId)")
    BigDecimal sumOutstandingDue(@Param("customerId") Integer customerId, @Param("excludeId") Integer excludeId);

    @Query("""
        SELECT part.serialNumbers
        FROM ServiceJob j
        JOIN j.productParts part
        WHERE j.status NOT IN (
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.DELIVERED,
                org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus.CANCELLED)
          AND j.paymentStatus IS NULL
          AND (:excludeJobId IS NULL OR j.id <> :excludeJobId)
          AND part.serialNumbers IS NOT NULL
          AND part.serialNumbers <> ''
        """)
    List<String> findUsedSerialNumberStrings(@Param("excludeJobId") Integer excludeJobId);
}
