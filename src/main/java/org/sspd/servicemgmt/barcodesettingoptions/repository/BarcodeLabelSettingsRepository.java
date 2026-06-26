package org.sspd.servicemgmt.barcodesettingoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.barcodesettingoptions.model.BarcodeLabelSettings;

@Repository
public interface BarcodeLabelSettingsRepository extends JpaRepository<BarcodeLabelSettings, Integer> {
}
