package org.sspd.servicemgmt.backupoptions.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceStep2Test {

    @Test
    void createsAndVerifiesReadableGzipBackup() throws Exception {
        Path file = Files.createTempFile("backup-step2-", ".sql.gz");
        try {
            try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(file))) {
                gzip.write("CREATE TABLE test (id INT);".getBytes());
            }
            assertTrue(Files.size(file) > 0);
            assertTrue(BackupService.isReadableGzip(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rejectsFailedCompressionVerification() throws Exception {
        Path file = Files.createTempFile("backup-step2-invalid-", ".sql.gz");
        try {
            Files.writeString(file, "not gzip data");
            assertFalse(BackupService.isReadableGzip(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void keepsLegacyBackupExtensionsSupported() {
        assertTrue(BackupService.isSupportedBackupFileName("ser_db.sql"));
        assertTrue(BackupService.isSupportedBackupFileName("ser_db.sqlbackup"));
        assertTrue(BackupService.isSupportedBackupFileName("ser_db_2026-08-19_05-30-00.sql.gz"));
        assertFalse(BackupService.isSupportedBackupFileName("ser_db.zip"));
    }

    @Test
    void retentionSelectsOnlyFilesBeyondConfiguredKeepCount() {
        List<Path> files = List.of(Path.of("new"), Path.of("middle"), Path.of("old"));
        assertEquals(List.of(Path.of("old")), BackupService.filesToDelete(files, 2));
        assertEquals(files, BackupService.filesToDelete(files, 0));
    }
}