package org.sspd.servicemgmt.appsettingsoptions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.sspd.servicemgmt.api.ApiResponse;
import org.sspd.servicemgmt.appsettingsoptions.dto.AppVersionSettingsDTO;
import org.sspd.servicemgmt.appsettingsoptions.service.AppVersionSettingsService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@RestController
@RequestMapping("/api/v1/app-version-settings")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AppVersionSettingsController {

    private final AppVersionSettingsService service;

    @Value("${app.apk.storage-dir}")
    private String apkStorageDir;

    @GetMapping
    public ResponseEntity<ApiResponse<AppVersionSettingsDTO>> getSettings() {
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", service.getSettings()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppVersionSettingsDTO>> saveSettings(
            @RequestBody AppVersionSettingsDTO dto) {
        return ResponseEntity.ok(new ApiResponse<>(true, "သိမ်းဆည်းပြီး", service.saveSettings(dto)));
    }

    @PostMapping("/upload-apk")
    public ResponseEntity<ApiResponse<String>> uploadApk(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.ok(new ApiResponse<>(false, "APK file မပါပါ", null));
            }
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (!originalName.endsWith(".apk")) {
                return ResponseEntity.ok(new ApiResponse<>(false, "APK file (.apk) သာ upload လုပ်ပါ", null));
            }
            Path dir = Paths.get(apkStorageDir);
            Files.createDirectories(dir);
            Path dest = dir.resolve("servicemgmt.apk");
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(new ApiResponse<>(true, "APK upload ပြီး", dest.toString()));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse<>(false, "Upload မအောင်မြင်ပါ: " + e.getMessage(), null));
        }
    }

    @GetMapping("/apk-exists")
    public ResponseEntity<ApiResponse<Boolean>> apkExists() {
        File f = new File(apkStorageDir, "servicemgmt.apk");
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", f.exists()));
    }
}
