package org.sspd.servicemgmt.servicejoboptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkPartResolution;

import java.util.List;
import java.time.LocalDateTime;

public interface ReworkPartResolutionRepository extends JpaRepository<ReworkPartResolution, Integer> {
    List<ReworkPartResolution> findByReworkJobIdOrderByIdAsc(Integer reworkJobId);
    boolean existsByOriginalPartId(Integer originalPartId);

    @org.springframework.data.jpa.repository.Query("select count(r) from ReworkPartResolution r where r.resolutionMode = org.sspd.servicemgmt.servicejoboptions.model.ReworkResolutionMode.UPGRADE and r.createdAt >= :from and r.createdAt < :to")
    long countUpgradesInPeriod(@org.springframework.data.repository.query.Param("from") LocalDateTime from, @org.springframework.data.repository.query.Param("to") LocalDateTime to);
}
