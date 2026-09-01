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
import org.sspd.servicemgmt.backupoptions.repository.BackupHistoryRepository;
import org.sspd.servicemgmt.backupoptions.config.BackupProperties;
import org.sspd.servicemgmt.backupoptions.model.BackupHistory;
import org.sspd.servicemgmt.backupoptions.model.BackupStatus;
import org.sspd.servicemgmt.backupoptions.model.BackupType;

import java.io.File;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;
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
    private final BackupHistoryRepository historyRepository;
    private final BackupProperties backupProperties;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.booking-photo.storage-dir:./booking-photo-storage}")
    private String bookingPhotoStorageDir;

    @Value("${app.product-photo.storage-dir:./product-photo-storage}")
    private String productPhotoStorageDir;

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
        s.setBackupDir((dto.getBackupDir() != null && !dto.getBackupDir().isBlank())
            ? dto.getBackupDir().trim() : backupProperties.getRootDirectory());
        s.setEnabled(dto.isEnabled());
        s.setKeepDays(clamp(dto.getKeepDays(), 1, 3650, 30));
        s.setMysqldumpPath(dto.getMysqldumpPath() != null ? dto.getMysqldumpPath().trim() : "");
        s.setDailyEnabled(dto.isDailyEnabled());
        s.setDailyTime(parseBackupTimeOrDefault(dto.getDailyTime(), LocalTime.of(5, 30)));
        s.setWeeklyEnabled(dto.isWeeklyEnabled());
        s.setWeeklyDay(clamp(dto.getWeeklyDay(), 1, 7, 7));
        s.setWeeklyTime(parseBackupTimeOrDefault(dto.getWeeklyTime(), LocalTime.of(5, 40)));
        s.setMonthlyEnabled(dto.isMonthlyEnabled());
        s.setMonthlyDay(clamp(dto.getMonthlyDay(), 1, 28, 1));
        s.setMonthlyTime(parseBackupTimeOrDefault(dto.getMonthlyTime(), LocalTime.of(5, 50)));
        BackupSettings saved = repository.save(s);
        schedulerService.reschedule(saved);
        return toDto(saved);
    }

    public String runNow() {
        return executeBackup(BackupType.MANUAL);
    }

    public String executeBackup(BackupSettings settings) {
        return executeBackup(BackupType.DAILY);
    }

    public String executeBackup(BackupType backupType) {
        BackupHistory history = BackupHistory.builder()
            .backupType(backupType).status(BackupStatus.RUNNING)
            .startedAt(LocalDateTime.now(BACKUP_ZONE)).build();
        history = historyRepository.save(history);
        try {
            String dbName = extractDbName(datasourceUrl);
            String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path dir = Paths.get(effectiveBackupRoot(), folderName(backupType));
            Files.createDirectories(dir);

            String fileName = dbName + "_" + timestamp + ".sql.gz";
            Path outFile = dir.resolve(fileName);

            BackupSettings settings = getOrCreate();
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
            Process process = pb.start();
            String errorOutput;
            try (InputStream dump = new BufferedInputStream(process.getInputStream());
                 OutputStream file = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(outFile)))) {
                dump.transferTo(file);
            }
            errorOutput = new String(process.getErrorStream().readAllBytes());
            int exit = process.waitFor();
            if (exit == 0 && Files.exists(outFile) && Files.size(outFile) > 0 && isReadableGzip(outFile)) {
                copyPhotoStorages(dir.resolve("photos"));
                history.setFileName(fileName);
                history.setFilePath(outFile.toAbsolutePath().toString());
                history.setFileSize(Files.size(outFile));
                history.setStatus(BackupStatus.SUCCESS);
                history.setCompletedAt(LocalDateTime.now(BACKUP_ZONE));
                historyRepository.save(history);
                cleanOldBackups();
                log.info("Backup success: {}", outFile);
                return fileName;
            }
            log.error("mysqldump exit {} — stderr: {}", exit, errorOutput);
            throw new IllegalStateException("Backup verification failed; exit=" + exit + ", stderr=" + errorOutput);
        } catch (Exception e) {
            log.error("Backup error: {}", e.getMessage(), e);
            history.setStatus(BackupStatus.FAILED);
            history.setCompletedAt(LocalDateTime.now(BACKUP_ZONE));
            history.setErrorMessage(e.getMessage());
            historyRepository.save(history);
            return null;
        }
    }

    public List<BackupFileDTO> listBackups() {
        try {
            File dir = new File(effectiveBackupRoot());
            if (!dir.exists()) return List.of();
            File[] files = backupFiles(dir);
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
        if (!isSupportedBackupFileName(name)) {
            throw new RuntimeException("Only .sql.gz, .sql or .sqlbackup files are supported");
        }

        Path tempFile = null;
        try {
            String safety = executeBackup(BackupType.SAFETY);
            if (safety == null) throw new IllegalStateException("Safety backup failed; restore cancelled");
            if (name.endsWith(".sql.gz")) restoreGzip(file.getInputStream());
            else restoreSql(file.getInputStream());
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

    public void restoreHistory(Long id) {
        BackupHistory history = historyRepository.findById(id).orElseThrow();
        if (history.getFilePath() == null || !Files.exists(Paths.get(history.getFilePath()))) {
            throw new IllegalStateException("Backup file does not exist");
        }
        String safety = executeBackup(BackupType.SAFETY);
        if (safety == null) throw new IllegalStateException("Safety backup failed; restore cancelled");
        try (InputStream in = Files.newInputStream(Paths.get(history.getFilePath()))) {
            restoreGzip(in);
            restorePhotoStorages(Paths.get(history.getFilePath()).getParent().resolve("photos"));
        } catch (Exception e) {
            log.error("Restore failed for backup {}", id, e);
            throw new IllegalStateException("Restore failed: " + e.getMessage(), e);
        }
    }

    private void restoreGzip(InputStream source) throws Exception {
        Path tempFile = Files.createTempFile("backup-restore-", ".sql");
        try {
            try (InputStream in = new GZIPInputStream(new BufferedInputStream(source))) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            String dbName = extractDbName(datasourceUrl);
            String mysql = resolveMysqlCommand(getOrCreate().getMysqldumpPath());
            ProcessBuilder pb = new ProcessBuilder(mysql, "-u", dbUsername, "-p" + dbPassword, dbName);
            pb.redirectInput(tempFile.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) throw new RuntimeException("Import failed: " + output);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void restoreSql(InputStream source) throws Exception {
        Path tempFile = Files.createTempFile("backup-restore-", ".sql");
        try {
            Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING);
            String mysql = resolveMysqlCommand(getOrCreate().getMysqldumpPath());
            ProcessBuilder pb = new ProcessBuilder(mysql, "-u", dbUsername, "-p" + dbPassword,
                extractDbName(datasourceUrl));
            pb.redirectInput(tempFile.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            if (process.waitFor() != 0) throw new RuntimeException("Import failed: " + output);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    static boolean isSupportedBackupFileName(String name) {
        return name != null && (name.endsWith(".sql.gz") || name.endsWith(".sql") || name.endsWith(".sqlbackup"));
    }

    static boolean isReadableGzip(Path file) {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) { }
            return true;
        } catch (Exception e) {
            log.error("GZIP verification failed for {}", file, e);
            return false;
        }
    }

    private String findMysqldump() {
        String configured = backupProperties.getMysqldumpPath();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        // Check PATH first, then common install locations.
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
        if (configuredMysqldumpPath == null || configuredMysqldumpPath.isBlank()) {
            configuredMysqldumpPath = backupProperties.getMysqldumpPath();
        }
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

    public List<BackupHistory> listHistory() {
        return historyRepository.findAllByOrderByStartedAtDesc();
    }

    public void deleteHistory(Long id) {
        BackupHistory history = historyRepository.findById(id).orElseThrow();
        try {
            if (history.getFilePath() != null) Files.deleteIfExists(Paths.get(history.getFilePath()));
        } catch (Exception e) {
            log.error("Cannot delete backup file {}", history.getFilePath(), e);
            throw new IllegalStateException("Backup file delete failed", e);
        }
        historyRepository.delete(history);
    }

    private String folderName(BackupType type) {
        return switch (type) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
            case MANUAL -> "Manual";
            case SAFETY -> "Safety";
        };
    }

    private void cleanOldBackups() {
        try {
            cleanFolder(BackupType.DAILY, backupProperties.getRetentionDaily());
            cleanFolder(BackupType.WEEKLY, backupProperties.getRetentionWeekly());
            cleanFolder(BackupType.MONTHLY, backupProperties.getRetentionMonthly());
            cleanFolder(BackupType.MANUAL, backupProperties.getRetentionManual());
            cleanFolder(BackupType.SAFETY, backupProperties.getRetentionSafety());
        } catch (Exception e) {
            log.warn("Cleanup error", e);
        }
    }

    private void cleanFolder(BackupType type, int keep) throws Exception {
        Path dir = Paths.get(effectiveBackupRoot(), folderName(type));
        if (!Files.isDirectory(dir)) return;
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".sql.gz"))
                .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed()).toList();
        }
        for (Path file : filesToDelete(files, keep)) {
            if (Files.deleteIfExists(file)) markFileDeleted(file);
        }
    }

    private void copyPhotoStorages(Path target) throws Exception {
        copyPhotoStorage(Paths.get(bookingPhotoStorageDir), target);
        copyPhotoStorage(Paths.get(productPhotoStorageDir), target.resolve("product-photos"));
    }

    private void copyPhotoStorage(Path source, Path target) throws Exception {
        if (!Files.isDirectory(source)) return;
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target)) throw new IllegalStateException("Invalid photo backup path");
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void restorePhotoStorages(Path source) throws Exception {
        copyPhotoStorage(source.resolve("booking-items"), Paths.get(bookingPhotoStorageDir).resolve("booking-items"));
        copyPhotoStorage(source.resolve("product-photos"), Paths.get(productPhotoStorageDir));
    }

    static List<Path> filesToDelete(List<Path> files, int keep) {
        return files.stream().skip(Math.max(keep, 0)).toList();
    }

    private void markFileDeleted(Path file) {
        historyRepository.findAllByOrderByStartedAtDesc().stream()
            .filter(history -> file.toAbsolutePath().toString().equals(history.getFilePath()))
            .forEach(history -> {
                history.setFileDeleted(true);
                historyRepository.save(history);
            });
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
            .backupDir(backupProperties.getRootDirectory())
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
        dto.setDailyEnabled(s.isDailyEnabled());
        dto.setDailyTime((s.getDailyTime() != null ? s.getDailyTime() : LocalTime.of(5, 30)).toString());
        dto.setWeeklyEnabled(s.isWeeklyEnabled());
        dto.setWeeklyDay(s.getWeeklyDay() != null ? s.getWeeklyDay() : 7);
        dto.setWeeklyTime((s.getWeeklyTime() != null ? s.getWeeklyTime() : LocalTime.of(5, 40)).toString());
        dto.setMonthlyEnabled(s.isMonthlyEnabled());
        dto.setMonthlyDay(s.getMonthlyDay() != null ? s.getMonthlyDay() : 1);
        dto.setMonthlyTime((s.getMonthlyTime() != null ? s.getMonthlyTime() : LocalTime.of(5, 50)).toString());
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
            File[] files = backupFiles(dir);
            return Arrays.stream(files)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .map(this::toFileDto)
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private File[] backupFiles(File root) {
        File[] legacyFiles = root.listFiles((d, n) -> n.endsWith(".sql") || n.endsWith(".sqlbackup"));
        java.util.stream.Stream<File> legacyStream = legacyFiles == null
            ? java.util.stream.Stream.empty() : Arrays.stream(legacyFiles);
        return java.util.stream.Stream.concat(legacyStream, Arrays.stream(new String[]{"Daily", "Weekly", "Monthly", "Manual", "Safety"})
            .map(name -> new File(root, name))
            .filter(File::isDirectory)
            .flatMap(folder -> {
                File[] children = folder.listFiles((d, n) -> n.endsWith(".sql.gz") || n.endsWith(".sql") || n.endsWith(".sqlbackup"));
                return children == null ? java.util.stream.Stream.<File>empty() : Arrays.stream(children);
            })).toArray(File[]::new);
    }

    private String effectiveBackupRoot() {
        BackupSettings settings = getOrCreate();
        return settings.getBackupDir() != null && !settings.getBackupDir().isBlank()
            ? settings.getBackupDir().trim() : backupProperties.getRootDirectory();
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

    private LocalTime parseBackupTimeOrDefault(String value, LocalTime fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalTime.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int actual = value != null ? value : fallback;
        return Math.max(min, Math.min(max, actual));
    }
}
