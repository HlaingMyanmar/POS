package org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.stockoptions.manufacturingoptions.formulaoptions.model.ManufacturingFormula;

import java.util.List;

public interface ManufacturingFormulaRepository extends JpaRepository<ManufacturingFormula, Integer> {
    List<ManufacturingFormula> findAllByOrderByNameAsc();
}
