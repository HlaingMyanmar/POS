package org.sspd.servicemgmt.backupoptions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.sspd.servicemgmt.backupoptions.model.BackupHistory;
import org.sspd.servicemgmt.backupoptions.model.BackupType;

import java.util.List;

public interface BackupHistoryRepository extends JpaRepository<BackupHistory, Long> {
    List<BackupHistory> findAllByOrderByStartedAtDesc();
    List<BackupHistory> findByBackupTypeOrderByStartedAtDesc(BackupType backupType);
}