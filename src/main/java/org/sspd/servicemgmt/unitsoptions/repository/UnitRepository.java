package org.sspd.servicemgmt.unitsoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.unitsoptions.model.Unit;

import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit,Long> {
    boolean existsByUnitName(String name);

    Optional<Unit>findByUnitName(String unitName);
}
