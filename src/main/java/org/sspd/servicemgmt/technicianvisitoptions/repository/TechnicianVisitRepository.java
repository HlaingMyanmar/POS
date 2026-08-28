package org.sspd.servicemgmt.technicianvisitoptions.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisit;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianVisitStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TechnicianVisitRepository extends JpaRepository<TechnicianVisit, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from TechnicianVisit v where v.staff.id = :staffId and v.status in :statuses order by v.id desc")
    List<TechnicianVisit> lockActive(
            @Param("staffId") Integer staffId,
            @Param("statuses") Collection<TechnicianVisitStatus> statuses
    );

    Optional<TechnicianVisit> findFirstByStaffIdAndStatusInOrderByIdDesc(
            Integer staffId,
            Collection<TechnicianVisitStatus> statuses
    );

    boolean existsByServiceJobId(Integer serviceJobId);

    List<TechnicianVisit> findByStartedAtGreaterThanEqualAndStartedAtLessThanEqualOrderByStartedAtDesc(
            LocalDateTime from,
            LocalDateTime to
    );
}
