package org.sspd.servicemgmt.shelflocationoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.shelflocationoptions.model.ShelfLocation;

import java.util.List;

@Repository
public interface ShelfLocationRepository extends JpaRepository<ShelfLocation, Integer> {
    List<ShelfLocation> findByActiveTrue();
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);
}
