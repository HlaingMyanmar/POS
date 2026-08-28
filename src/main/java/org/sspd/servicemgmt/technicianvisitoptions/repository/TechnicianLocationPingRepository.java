package org.sspd.servicemgmt.technicianvisitoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianLocationPing;

import java.time.LocalDateTime;
import java.util.List;

public interface TechnicianLocationPingRepository extends JpaRepository<TechnicianLocationPing, Long> {

    boolean existsByClientPingId(String id);

    List<TechnicianLocationPing> findTop20ByVisit_IdOrderByRecordedAtDesc(Long visitId);

    @Modifying
    @Query("delete from TechnicianLocationPing p where p.recordedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
