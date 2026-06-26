package org.sspd.servicemgmt.backupoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.sspd.servicemgmt.backupoptions.model.BackupSettings;

@Repository
public interface BackupSettingsRepository extends JpaRepository<BackupSettings, Integer> {
}
