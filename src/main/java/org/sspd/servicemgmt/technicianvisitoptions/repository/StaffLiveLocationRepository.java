package org.sspd.servicemgmt.technicianvisitoptions.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.technicianvisitoptions.model.StaffLiveLocation;
import java.util.Optional;
public interface StaffLiveLocationRepository extends JpaRepository<StaffLiveLocation,Long> { Optional<StaffLiveLocation> findByStaffId(Integer staffId); }
