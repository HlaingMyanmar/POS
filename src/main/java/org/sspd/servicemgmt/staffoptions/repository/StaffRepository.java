package org.sspd.servicemgmt.staffoptions.repository;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.staffoptions.model.Staff;
import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    boolean existsByPhone(String phone);

    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    List<Staff> findAllByIsActiveTrue();
}