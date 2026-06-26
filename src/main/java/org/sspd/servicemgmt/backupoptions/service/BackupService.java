package org.sspd.servicemgmt.backupoptions.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.backupoptions.dto.BackupFileDTO;
import org.sspd.servicemgmt.backupoptions.dto.BackupSettingsDTO;
import org.sspd.servicemgmt.backupoptions.model.BackupFrequency;
import org.sspd.servicemgmt.backupoptions.model.BackupSettings;
import org.sspd.servicemgmt.backupoptions.repository.BackupSettingsRepository;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {
    private static final ZoneId BACKUP_ZONE = ZoneId.of("Asia/Rangoon");

    private final BackupSettingsRepository repository;
    private final BackupSchedulerService schedulerService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Transactional(readOnly = true)
    public BackupSettingsDTO getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional
    public BackupSettingsDTO saveSettings(BackupSettingsDTO dto) {
        BackupSettings s = getOrCreate();
        BackupFrequency frequency = dto.getFrequency() != null ? dto.getFrequency() : BackupFrequency.DAILY;
        s.setFrequency(frequency);
        s.setDayValue(clamp(dto.getDayValue(), 1, frequency == BackupFrequency.WEEKLY ? 7 : 28, 1));
        s.setMonthValue(clamp(dto.getMonthValue(), 1, 12, 1));
        s.setBackupTime(parseBackupTime(dto.getBackupTime()));
        s.setBackupDir((dto.getBackupDir() != null && !dto.getBackupDir().isBlank()) ? dto.getBackupDir().trim() : "./backups");
        s.setEnabled(dto.isEnabled());
        s.setKeepDays(clamp(dto.getKeepDays(), 1, 3650, 30));
        s.setMysqldumpPath(dto.getMysqldumpPath() != null ? dto.getMysqldumpPath().trim() : "");
        BackupSettings saved = repository.save(s);
        schedulerService.reschedule(saved);
        return toDto(saved);
    }

    public String runNow() {
        return executeBackup(getOrCreate());
    }

    public String executeBackup(BackupSettings settings) {
        try {
            String dbName = extractDbName(datasourceUrl);
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path dir = Paths.get(settings.getBackupDir());
            Files.createDirectories(dir);

            String fileName = dbName + "_" + timestamp + ".sql";
            Path outFile = dir.resolve(fileName);

            String mysqldump = (settings.getMysqldumpPath() != null && !settings.getMysqldumpPath().isBlank())
                ? settings.getMysqldumpPath()
                : findMysqldump();
            log.info("Using mysqldump at: {}", mysqldump);

            ProcessBuilder pb = new ProcessBuilder(
                mysqldump,
                "-u", dbUsername,
                "-p" + dbPassword,
                "--single-transaction",
                "--routines",
                "--triggers",
                dbName
            );
            pb.redirectOutput(outFile.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            String errorOutput = new String(process.getErrorStream().readAllBytes());
            int exit = process.waitFor();

            if (exit == 0) {
                cleanOldBackups(settings);
                log.info("Backup success: {}", fileName);
                return fileName;
            }
            log.error("mysqldump exit {} — stderr: {}", exit, errorOutput);
            return null;
        } catch (Exception e) {
            log.error("Backup error: {}", e.getMessage(), e);
            return null;
        }
    }

    public List<BackupFileDTO> listBackups() {
        try {
            File dir = new File(getOrCreate().getBackupDir());
            if (!dir.exists()) return List.of();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".sql"));
            if (files == null) return List.of();
            return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .map(this::toFileDto)
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void importBackup(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Backup file is required");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!name.endsWith(".sql")) {
            throw new RuntimeException("Only .sql file is supported");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("backup-restore-", ".sql");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String dbName = extractDbName(datasourceUrl);
            String mysql = resolveMysqlCommand(getOrCreate().getMysqldumpPath());

            ProcessBuilder pb = new ProcessBuilder(
                mysql,
                "-u", dbUsername,
                "-p" + dbPassword,
                dbName
            );
            pb.redirectInput(tempFile.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new RuntimeException("Import failed: " + output);
            }
            log.info("Backup import success: {}", file.getOriginalFilename());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String findMysqldump() {
        // Check PATH first
        String[] candidates = System.getProperty("os.name", "").toLowerCase().contains("win")
            ? new String[]{
                "mysqldump",
                "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysqldump.exe",
                "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe",
                "C:\\Program Files\\MySQL\\MySQL Server 5.7\\bin\\mysqldump.exe",
                "C:\\xampp\\mysql\\bin\\mysqldump.exe",
                "C:\\wamp64\\bin\\mysql\\mysql8.0\\bin\\mysqldump.exe",
              }
            : new String[]{
                "mysqldump",
                "/usr/bin/mysqldump",
                "/usr/local/bin/mysqldump",
              };

        for (String candidate : candidates) {
            if (candidate.equals("mysqldump")) return candidate; // trust PATH first
            if (new File(candidate).exists()) return candidate;
        }
        return "mysqldump"; // fallback — let OS resolve
    }

    private String resolveMysqlCommand(String configuredMysqldumpPath) {
        if (configuredMysqldumpPath != null && !configuredMysqldumpPath.isBlank()) {
            String trimmed = configuredMysqldumpPath.trim();
            if (trimmed.toLowerCase().endsWith("mysqldump.exe")) {
                return trimmed.substring(0, trimmed.length() - "mysqldump.exe".length()) + "mysql.exe";
            }
            if (trimmed.toLowerCase().endsWith("mysqldump")) {
                return trimmed.substring(0, trimmed.length() - "mysqldump".length()) + "mysql";
            }
        }

        String[] candidates = System.getProperty("os.name", "").toLowerCase().contains("win")
            ? new String[]{
                "mysql",
                "C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe",
                "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe",
                "C:\\Program Files\\MySQL\\MySQL Server 5.7\\bin\\mysql.exe",
                "C:\\xampp\\mysql\\bin\\mysql.exe",
                "C:\\wamp64\\bin\\mysql\\mysql8.0\\bin\\mysql.exe",
            }
            : new String[]{
                "mysql",
                "/usr/bin/mysql",
                "/usr/local/bin/mysql",
            };

        for (String candidate : candidates) {
            if (candidate.equals("mysql")) return candidate;
            if (new File(candidate).exists()) return candidate;
        }
        return "mysql";
    }

    private void cleanOldBackups(BackupSettings s) {
        try {
            File dir = new File(s.getBackupDir());
            if (!dir.exists()) return;
            File[] files = dir.listFiles((d, n) -> n.endsWith(".sql"));
            if (files == null) return;
            LocalDate cutoff = LocalDate.now(BACKUP_ZONE).minusDays(s.getKeepDays());
            for (File f : files) {
                LocalDate modifiedDate = Instant.ofEpochMilli(f.lastModified()).atZone(BACKUP_ZONE).toLocalDate();
                if (modifiedDate.isBefore(cutoff))
                    f.delete();
            }
        } catch (Exception e) {
            log.warn("Cleanup error", e);
        }
    }

    private String extractDbName(String url) {
        String[] parts = url.split("/");
        String last = parts[parts.length - 1];
        return last.contains("?") ? last.substring(0, last.indexOf("?")) : last;
    }

    @SuppressWarnings("null")
    private BackupSettings getOrCreate() {
        List<BackupSettings> all = repository.findAll();
        if (!all.isEmpty()) return all.get(0);
        return repository.save(BackupSettings.builder()
            .frequency(BackupFrequency.DAILY)
            .backupTime(LocalTime.of(2, 0))
            .backupDir("./backups")
            .enabled(true)
            .keepDays(30)
            .build());
    }

    private BackupSettingsDTO toDto(BackupSettings s) {
        BackupSettingsDTO dto = new BackupSettingsDTO();
        dto.setId(s.getId());
        dto.setFrequency(s.getFrequency());
        dto.setDayValue(s.getDayValue());
        dto.setMonthValue(s.getMonthValue());
        dto.setBackupTime(s.getBackupTime().toString());
        dto.setBackupDir(s.getBackupDir());
        dto.setEnabled(s.isEnabled());
        dto.setKeepDays(s.getKeepDays());
        dto.setMysqldumpPath(s.getMysqldumpPath());
        dto.setNextRunAt(s.isEnabled() ? nextRunAt(s).toLocalDateTime().toString() : null);

        File dir = new File(s.getBackupDir());
        dto.setBackupDirExists(dir.exists());
        dto.setBackupDirWritable(dir.exists() && dir.canWrite());

        List<BackupFileDTO> files = listBackupsForSettings(s);
        dto.setBackupCount(files.size());
        if (!files.isEmpty()) {
            BackupFileDTO latest = files.get(0);
            dto.setLastBackupFile(latest.getFileName());
            dto.setLastBackupAt(latest.getModifiedAt());
            dto.setLastBackupSizeBytes(latest.getSizeBytes());
        }
        return dto;
    }

    private List<BackupFileDTO> listBackupsForSettings(BackupSettings settings) {
        try {
            File dir = new File(settings.getBackupDir());
            if (!dir.exists()) return List.of();
            File[] files = dir.listFiles((d, n) -> n.endsWith(".sql"));
            if (files == null) return List.of();
            return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .map(this::toFileDto)
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private BackupFileDTO toFileDto(File file) {
        LocalDateTime modifiedAt = Instant.ofEpochMilli(file.lastModified()).atZone(BACKUP_ZONE).toLocalDateTime();
        long ageDays = ChronoUnit.DAYS.between(modifiedAt.toLocalDate(), LocalDate.now(BACKUP_ZONE));
        return BackupFileDTO.builder()
            .fileName(file.getName())
            .sizeBytes(file.length())
            .modifiedAt(modifiedAt.toString())
            .ageDays(Math.max(ageDays, 0))
            .build();
    }

    private ZonedDateTime nextRunAt(BackupSettings settings) {
        ZonedDateTime now = ZonedDateTime.now(BACKUP_ZONE);
        LocalTime time = settings.getBackupTime() != null ? settings.getBackupTime() : LocalTime.of(2, 0);
        BackupFrequency frequency = settings.getFrequency() != null ? settings.getFrequency() : BackupFrequency.DAILY;

        return switch (frequency) {
            case DAILY -> {
                ZonedDateTime next = atBackupTime(now, time);
                yield next.isAfter(now) ? next : next.plusDays(1);
            }
            case WEEKLY -> {
                int day = clamp(settings.getDayValue(), 1, 7, 1);
                ZonedDateTime next = atBackupTime(now.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day))), time);
                yield next.isAfter(now) ? next : next.plusWeeks(1);
            }
            case MONTHLY -> {
                int day = clamp(settings.getDayValue(), 1, 28, 1);
                ZonedDateTime next = atBackupTime(now.withDayOfMonth(Math.min(day, YearMonth.from(now).lengthOfMonth())), time);
                yield next.isAfter(now) ? next : atBackupTime(now.plusMonths(1).withDayOfMonth(day), time);
            }
            case YEARLY -> {
                int month = clamp(settings.getMonthValue(), 1, 12, 1);
                int day = clamp(settings.getDayValue(), 1, 28, 1);
                ZonedDateTime next = atBackupTime(now.withMonth(month).withDayOfMonth(day), time);
                yield next.isAfter(now) ? next : atBackupTime(now.plusYears(1).withMonth(month).withDayOfMonth(day), time);
            }
        };
    }

    private ZonedDateTime atBackupTime(ZonedDateTime date, LocalTime time) {
        return date.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);
    }

    private LocalTime parseBackupTime(String value) {
        if (value == null || value.isBlank()) return LocalTime.of(2, 0);
        return LocalTime.parse(value);
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int actual = value != null ? value : fallback;
        return Math.max(min, Math.min(max, actual));
    }
}
