package org.sspd.servicemgmt.servicejoboptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.servicejoboptions.model.ReworkPartResolution;

import java.util.List;

public interface ReworkPartResolutionRepository extends JpaRepository<ReworkPartResolution, Integer> {
    List<ReworkPartResolution> findByReworkJobIdOrderByIdAsc(Integer reworkJobId);
}
