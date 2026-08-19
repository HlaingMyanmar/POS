package org.sspd.servicemgmt.backupoptions.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.sspd.servicemgmt.backupoptions.model.BackupSettings;
import org.sspd.servicemgmt.backupoptions.repository.BackupSettingsRepository;

import java.util.TimeZone;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.sspd.servicemgmt.backupoptions.model.BackupType;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupSchedulerService {

    private final TaskScheduler backupTaskScheduler;
    private final BackupSettingsRepository repository;

    @Autowired @Lazy
    private BackupService backupService;

    private final Map<BackupType, ScheduledFuture<?>> tasks = new EnumMap<>(BackupType.class);

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
        cancel(BackupType.DAILY);
        cancel(BackupType.WEEKLY);
        cancel(BackupType.MONTHLY);
        if (!settings.isEnabled()) return;
        schedule(settings, BackupType.DAILY, settings.isDailyEnabled(), settings.getDailyTime() != null ? settings.getDailyTime() : java.time.LocalTime.of(5, 30), "*", 0);
        schedule(settings, BackupType.WEEKLY, settings.isWeeklyEnabled(), settings.getWeeklyTime() != null ? settings.getWeeklyTime() : java.time.LocalTime.of(5, 40), weeklyDayName(settings.getWeeklyDay()), 0);
        schedule(settings, BackupType.MONTHLY, settings.isMonthlyEnabled(), settings.getMonthlyTime() != null ? settings.getMonthlyTime() : java.time.LocalTime.of(5, 50), "*", settings.getMonthlyDay() != null ? settings.getMonthlyDay() : 1);
    }

    private void schedule(BackupSettings settings, BackupType type, boolean enabled, java.time.LocalTime time, String day, int monthDay) {
        if (!enabled) return;
        String cron = type == BackupType.MONTHLY
            ? String.format("0 %d %d %d * *", time.getMinute(), time.getHour(), monthDay)
            : String.format("0 %d %d * * %s", time.getMinute(), time.getHour(), day);
        log.info("Backup {} scheduled: {}", type, cron);
        tasks.put(type, backupTaskScheduler.schedule(() -> backupService.executeBackup(type),
            new CronTrigger(cron, TimeZone.getTimeZone("Asia/Rangoon"))));
    }

    private void cancel(BackupType type) {
        ScheduledFuture<?> task = tasks.remove(type);
        if (task != null) task.cancel(false);
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
