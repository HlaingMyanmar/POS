package org.sspd.servicemgmt.backupoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.sspd.servicemgmt.backupoptions.model.BackupType;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "backup.property-scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class BackupScheduler {
    private final BackupService backupService;

    @Scheduled(cron = "${backup.daily.cron}", zone = "${backup.time-zone:Asia/Rangoon}")
    public void daily() { run(BackupType.DAILY); }

    @Scheduled(cron = "${backup.weekly.cron}", zone = "${backup.time-zone:Asia/Rangoon}")
    public void weekly() { run(BackupType.WEEKLY); }

    @Scheduled(cron = "${backup.monthly.cron}", zone = "${backup.time-zone:Asia/Rangoon}")
    public void monthly() { run(BackupType.MONTHLY); }

    private void run(BackupType type) {
        log.info("Starting scheduled {} database backup", type);
        backupService.executeBackup(type);
    }
}