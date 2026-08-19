package org.sspd.servicemgmt.shelflocationoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.shelflocationoptions.model.ShelfLocation;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShelfLocationRepository extends JpaRepository<ShelfLocation, Integer> {
    List<ShelfLocation> findByActiveTrue();
    Optional<ShelfLocation> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);
}
