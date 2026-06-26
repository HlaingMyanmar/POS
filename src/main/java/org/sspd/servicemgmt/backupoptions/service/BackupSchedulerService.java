package org.sspd.servicemgmt.backupoptions.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.backupoptions.model.BackupSettings;
import org.sspd.servicemgmt.backupoptions.repository.BackupSettingsRepository;

import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupSchedulerService {

    private final TaskScheduler backupTaskScheduler;
    private final BackupSettingsRepository repository;

    @Autowired @Lazy
    private BackupService backupService;

    private ScheduledFuture<?> currentTask;

    @PostConstruct
    void init() {
        BackupSettings settings = repository.findAll().stream().findFirst()
            .orElseGet(() -> repository.save(BackupSettings.builder()
                .frequency(org.sspd.servicemgmt.backupoptions.model.BackupFrequency.DAILY)
                .backupTime(java.time.LocalTime.of(2, 0))
                .backupDir("./backups")
                .enabled(true)
                .keepDays(30)
                .build()));
        reschedule(settings);
    }

    public void reschedule(BackupSettings settings) {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        if (!settings.isEnabled()) {
            log.info("Backup scheduler disabled");
            return;
        }
        String cron = buildCron(settings);
        log.info("Backup scheduled: {}", cron);
        currentTask = backupTaskScheduler.schedule(
            () -> backupService.executeBackup(settings),
            new CronTrigger(cron, TimeZone.getTimeZone("Asia/Rangoon"))
        );
    }

    private String buildCron(BackupSettings s) {
        int h = s.getBackupTime().getHour();
        int m = s.getBackupTime().getMinute();
        return switch (s.getFrequency()) {
            case DAILY   -> String.format("0 %d %d * * *", m, h);
            case WEEKLY  -> String.format("0 %d %d * * %s", m, h, weeklyDayName(s.getDayValue()));
            case MONTHLY -> String.format("0 %d %d %d * *", m, h,
                              s.getDayValue() != null ? s.getDayValue() : 1);
            case YEARLY  -> String.format("0 %d %d %d %d *", m, h,
                              s.getDayValue()   != null ? s.getDayValue()   : 1,
                              s.getMonthValue() != null ? s.getMonthValue() : 1);
        };
    }

    private String weeklyDayName(Integer dayValue) {
        int normalized = dayValue != null ? dayValue : 1;
        return switch (normalized) {
            case 1 -> "MON";
            case 2 -> "TUE";
            case 3 -> "WED";
            case 4 -> "THU";
            case 5 -> "FRI";
            case 6 -> "SAT";
            case 7 -> "SUN";
            default -> "MON";
        };
    }
}
