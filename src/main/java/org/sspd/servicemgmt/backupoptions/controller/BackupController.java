package org.sspd.servicemgmt.backupoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.backupoptions.dto.BackupFileDTO;
import org.sspd.servicemgmt.backupoptions.dto.BackupSettingsDTO;
import org.sspd.servicemgmt.backupoptions.service.BackupService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/backup")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_SETTINGS_READ')")
    @GetMapping("/settings")
    ResponseEntity<ApiResponse<BackupSettingsDTO>> getSettings() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Backup settings", backupService.getSettings()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_SETTINGS_UPDATE')")
    @PostMapping("/settings")
    ResponseEntity<ApiResponse<BackupSettingsDTO>> saveSettings(@RequestBody BackupSettingsDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Saved", backupService.saveSettings(dto)));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_RUN')")
    @PostMapping("/run-now")
    ResponseEntity<ApiResponse<String>> runNow() {
        String result = backupService.runNow();
        boolean ok = result != null;
        return ResponseEntity.ok(new ApiResponse<>(ok, ok ? "Backup completed: " + result : "Backup failed", result));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_FILES_READ')")
    @GetMapping("/list")
    ResponseEntity<ApiResponse<List<BackupFileDTO>>> listBackups() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Backup files", backupService.listBackups()));
    }

    @PreAuthorize("hasAuthority('CAN_ACCESS_BACKUP_IMPORT')")
    @PostMapping("/import")
    ResponseEntity<ApiResponse<Void>> importBackup(@RequestParam("file") MultipartFile file) {
        backupService.importBackup(file);
        return ResponseEntity.ok(new ApiResponse<>(true, "Import completed", null));
    }
}
