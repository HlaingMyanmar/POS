package org.sspd.servicemgmt.barcodesettingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.barcodesettingoptions.model.BarcodeLabelPreset;

import java.util.List;

@Repository
public interface BarcodeLabelPresetRepository extends JpaRepository<BarcodeLabelPreset, Integer> {
    List<BarcodeLabelPreset> findAllByOrderByIdAsc();
    boolean existsByName(String name);
}
