package org.sspd.servicemgmt.backupoptions.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.backupoptions.model.BackupHistory;
import org.sspd.servicemgmt.backupoptions.service.BackupService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/backups")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BackupHistoryController {
    private final BackupService backupService;

    @PostMapping
    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_RUN')")
    public ResponseEntity<ApiResponse<String>> create() {
        String file = backupService.runNow();
        if (file == null) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiResponse<>(false, "Backup failed", null));
        return ResponseEntity.ok(new ApiResponse<>(true, "Backup completed", file));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_FILES_READ')")
    public ResponseEntity<ApiResponse<List<BackupHistory>>> list() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Backup history", backupService.listHistory()));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_IMPORT')")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable Long id) {
        try {
            backupService.restoreHistory(id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Restore completed", null));
        } catch (Exception e) {
            log.error("Backup restore failed for {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_RUN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        try {
            backupService.deleteHistory(id);
            return ResponseEntity.ok(new ApiResponse<>(true, "Backup deleted", null));
        } catch (Exception e) {
            log.error("Backup delete failed for {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }
}
