package org.sspd.servicemgmt.technicianvisitoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.technicianvisitoptions.model.TechnicianLocationPing;

import java.util.List;

public interface TechnicianLocationPingRepository extends JpaRepository<TechnicianLocationPing, Long> {

    boolean existsByClientPingId(String id);

    List<TechnicianLocationPing> findTop20ByVisit_IdOrderByRecordedAtDesc(Long visitId);
    List<TechnicianLocationPing> findByVisit_IdOrderByRecordedAtAscIdAsc(Long visitId);
    int deleteByVisit_Id(Long visitId);

}
