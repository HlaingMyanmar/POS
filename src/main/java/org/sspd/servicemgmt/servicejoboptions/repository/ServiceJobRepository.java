package org.sspd.servicemgmt.servicejoboptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJob;
import org.sspd.servicemgmt.servicejoboptions.model.ServiceJobStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceJobRepository extends JpaRepository<ServiceJob, Integer> {
    Optional<ServiceJob> findTopByOrderByIdDesc();
    List<ServiceJob> findByStatus(ServiceJobStatus status);
    List<ServiceJob> findByStatusAndPaymentStatusIsNullOrderByReceivedDateDesc(ServiceJobStatus status);
    List<ServiceJob> findByCustomerId(Integer customerId);
    List<ServiceJob> findByAssignedStaffId(Integer staffId);
    long countByStatus(ServiceJobStatus status);
    Optional<ServiceJob> findByBookingId(Integer bookingId);

    @Query("""
        SELECT j FROM ServiceJob j
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(j.jobNo) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(j.customer.name) LIKE LOWER(CONCAT('%',:search,'%'))
               OR LOWER(j.itemName) LIKE LOWER(CONCAT('%',:search,'%')))
          AND (:dateFrom IS NULL OR j.receivedDate >= :dateFrom)
          AND (:dateTo   IS NULL OR j.receivedDate <  :dateTo)
        """)
    org.springframework.data.domain.Page<ServiceJob> findBySearchAndDate(
            @Param("search")   String search,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo")   LocalDateTime dateTo,
            org.springframework.data.domain.Pageable pageable);

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
