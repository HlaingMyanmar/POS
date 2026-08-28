# Backup and restore

Implementation: `backupoptions/service/BackupService.java`, `BackupSchedulerService.java`, `BackupScheduler.java`, `BackupProperties.java`. APIs: `/api/v1/backup` and `/api/backups`.

This is a **logical mysqldump** of the configured database, gzipped to disk. It is not a filesystem snapshot of the JVM, APK folder, or keystore.

---

## Backup trigger

| Trigger | How |
|---|---|
| Manual | `POST /api/v1/backup/run-now` or `POST /api/backups` (`CAN_ACCESS_BACKUP_RUN`) → `BackupService.runNow()` → `executeBackup(MANUAL)` |
| Scheduled (DB settings) | `BackupSchedulerService` (`TaskScheduler` bean `backup-`). After boot and after `POST /api/v1/backup/settings`, it registers DAILY / WEEKLY / MONTHLY cron jobs when `backup_settings.enabled` is true **and** the matching `dailyEnabled` / `weeklyEnabled` / `monthlyEnabled` flag is true. Zone: **Asia/Rangoon**. |
| Property cron | `BackupScheduler` `@Scheduled` methods. Bean exists only if `backup.property-scheduler.enabled=true`. Checked-in value is **`false`**, so these crons do **not** run unless that property is changed. |
| Pre-restore / pre-import | `executeBackup(SAFETY)` inside `importBackup` and `restoreHistory`. If SAFETY fails, restore is cancelled. |

`executeBackup(BackupSettings)` (the overload that takes settings) always runs **`BackupType.DAILY`**, not the settings frequency. Callers of that overload: **Needs Confirmation** (scheduled path uses `executeBackup(BackupType)`).

---

## Scheduler details

`BackupSchedulerService.init()` (`@PostConstruct`): loads the first `backup_settings` row, or inserts defaults (`frequency=DAILY`, time `02:00`, dir `./backups`, `enabled=true`, `keepDays=30`) then `reschedule`.

Cron built in `schedule(...)`:

- Daily: `0 <min> <hour> * * *`
- Weekly: `0 <min> <hour> * * <dayName>` (`weeklyDay` 1–7)
- Monthly: `0 <min> <hour> <monthDay> * *`

If `enabled` is false, all three tasks are cancelled and nothing is scheduled.

Property-file crons (inactive unless the flag is on): `backup.daily.cron`, `backup.weekly.cron`, `backup.monthly.cron`, zone `backup.time-zone` (default Asia/Rangoon).

---

## Backup location

Root: `BackupSettings.backupDir` if set, else `backup.root-directory` (default `./Backup`). Relative paths are relative to the **JVM working directory**.

Subfolders (`BackupService.folderName`):

| Type | Folder |
|---|---|
| DAILY | `Daily` |
| WEEKLY | `Weekly` |
| MONTHLY | `Monthly` |
| MANUAL | `Manual` |
| SAFETY | `Safety` |

---

## Filename format

```text
{databaseName}_{yyyy-MM-dd_HH-mm-ss}.sql.gz
```

`databaseName` is parsed from `spring.datasource.url` (segment after the last `/`, query string stripped). Timestamp uses `LocalDateTime.now()` **without** an explicit zone (history timestamps use Asia/Rangoon).

History row: `backup_history` with type, status `RUNNING` → `SUCCESS` or `FAILED`, `fileName`, `filePath` (absolute), `fileSize`, `startedAt` / `completedAt`, `errorMessage`.

---

## How a dump is taken

Process:

```text
mysqldump -u <datasource.username> -p<datasource.password> --single-transaction --routines --triggers <dbName>
```

Stdout is gzipped to the output file. `mysqldump` path: `backup_settings.mysqldumpPath` if non-blank, else PATH / well-known Windows and Unix locations (`findMysqldump`).

Password is passed on the process command line (`-p` + password).

---

## Integrity verification

A run is SUCCESS only if:

1. process exit code is 0
2. output file exists
3. file size > 0
4. `isReadableGzip` can fully read the file as GZIP

Otherwise the history row is FAILED and `runNow` / `executeBackup` returns `null`.

---

## Retention

After a successful dump, `cleanOldBackups()` keeps the newest N `.sql.gz` files **per folder**, using **`BackupProperties`** (not `backup_settings.keepDays`):

| Folder | Property | Checked-in default |
|---|---|---|
| Daily | `backup.retention.daily` | 7 |
| Weekly | `backup.retention.weekly` | 4 |
| Monthly | `backup.retention.monthly` | 12 |
| Manual | `backup.retention.manual` | 30 |
| Safety | `backup.retention.safety` | 7 |

Deleted files get matching history rows flagged `fileDeleted=true`. `keepDays` is stored and returned in the settings DTO but is **not** used by `cleanOldBackups`.

---

## Restore procedure

### From history (gzip on disk)

**Trigger:** `POST /api/backups/{id}/restore` (`CAN_ACCESS_BACKUP_IMPORT`).

1. Load `backup_history`; fail if `filePath` missing or file gone.
2. SAFETY backup; abort if that returns null.
3. `restoreGzip`: gunzip to a temp `.sql`, then:

```text
mysql -u <user> -p<password> <dbName>  < temp.sql
```

`mysql` binary is derived from `mysqldumpPath` (replace `mysqldump` / `mysqldump.exe` with `mysql` / `mysql.exe`) or PATH / well-known locations.

History restore **always** uses `restoreGzip`. Files produced by this app are `.sql.gz`, so that matches. Restoring a history row that pointed at a plain `.sql` would be wrong — those rows are not created by `executeBackup`.

Non-zero `mysql` exit → `IllegalStateException`; controller returns HTTP 500 with the message.

### From upload

**Trigger:** `POST /api/v1/backup/import` multipart field `file`.

Allowed names (lowercase): `.sql.gz`, `.sql`, `.sqlbackup`.

1. SAFETY backup; abort if null.
2. `.sql.gz` → `restoreGzip`; `.sql` / `.sqlbackup` → `restoreSql` (copy stream, then same `mysql` invoke).

There is **no** download endpoint in `BackupController` / `BackupHistoryController`. Listing: `GET /api/v1/backup/list` (files) and `GET /api/backups` (history). Delete: `DELETE /api/backups/{id}` (file then row).

---

## Pre-restore backup

Both import and history restore call `executeBackup(BackupType.SAFETY)` first. Failure message: `Safety backup failed; restore cancelled`.

SAFETY files live under `{root}/Safety/` and are subject to `backup.retention.safety`.

---

## Failure handling

| Stage | Behaviour |
|---|---|
| Dump / gzip verify fail | History `FAILED`, error message stored, method returns `null`. Manual API: `BackupController` still HTTP 200 with `success=false`; `BackupHistoryController.create` returns **500**. |
| SAFETY fail | Restore/import not started. |
| `mysql` fail | Exception; import wraps as `RuntimeException("Import failed: …")`. |
| Missing file on restore | `IllegalStateException("Backup file does not exist")`. |
| Cleanup fail | Logged at warn; dump still SUCCESS. |

Restore **replaces database contents** with whatever is in the dump (standard `mysql` client behaviour). The app process is **not** stopped by this code. Hibernate L2 cache / in-memory JWT state after restore: **Needs Confirmation** (restart is the safe assumption, not implemented here).

---

## Permissions

| Permission | Endpoints |
|---|---|
| `CAN_ACCESS_BACKUP_SETTINGS_READ` | GET `/api/v1/backup/settings` |
| `CAN_ACCESS_BACKUP_SETTINGS_UPDATE` | POST `/api/v1/backup/settings` |
| `CAN_ACCESS_BACKUP_RUN` | POST run-now, POST `/api/backups`, DELETE `/api/backups/{id}` |
| `CAN_ACCESS_BACKUP_FILES_READ` | GET list / history |
| `CAN_ACCESS_BACKUP_IMPORT` | POST import, POST restore |

---

## Documentation findings (backup)

- `backup.property-scheduler.enabled=false` — property cron class is off; DB-driven `BackupSchedulerService` is what actually schedules.
- `backup.legacy-settings-scheduler.enabled` is unused in Java.
- `keepDays` on settings is not the retention used by cleanup.
- `executeBackup(BackupSettings)` always dumps as DAILY.
- Dump password on the OS command line.
- Dual API prefixes (`/api/v1/backup` vs `/api/backups`).
- History restore assumes gzip even though import accepts plain SQL.
