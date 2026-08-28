package org.sspd.servicemgmt.api;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.sspd.servicemgmt.appsettingsoptions.model.AppVersionSettings;
import org.sspd.servicemgmt.appsettingsoptions.service.AppVersionSettingsService;

import java.io.File;

@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppVersionSettingsService versionSettingsService;

    @Value("${app.download.base-url}")
    private String downloadBaseUrl;

    @Value("${app.apk.storage-dir}")
    private String apkStorageDir;

    @GetMapping("/version")
    public ResponseEntity<ApiResponse<AppVersionResponse>> getVersion() {
        AppVersionSettings s = versionSettingsService.getOrCreate();
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", toResponse(
                s.getVersionCode(),
                s.getVersionName(),
                s.isForceUpdate(),
                s.getChangelog(),
                "servicemgmt.apk"
        )));
    }

    @GetMapping("/technician/version")
    public ResponseEntity<ApiResponse<AppVersionResponse>> getTechnicianVersion() {
        AppVersionSettings s = versionSettingsService.getOrCreate();
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", toResponse(
                s.getTechnicianVersionCode() == null ? 1 : s.getTechnicianVersionCode(),
                s.getTechnicianVersionName() == null ? "1.0.0" : s.getTechnicianVersionName(),
                s.isTechnicianForceUpdate(),
                s.getTechnicianChangelog(),
                "technician.apk"
        )));
    }

    private AppVersionResponse toResponse(
            Integer versionCode,
            String versionName,
            boolean forceUpdate,
            String changelog,
            String apkFileName
    ) {
        String base = downloadBaseUrl.endsWith("/")
                ? downloadBaseUrl.substring(0, downloadBaseUrl.length() - 1)
                : downloadBaseUrl;
        boolean apkReady = new File(apkStorageDir, apkFileName).exists();
        AppVersionResponse v = new AppVersionResponse();
        v.setVersionCode(versionCode == null ? 0 : versionCode);
        v.setVersionName(versionName == null ? "" : versionName);
        v.setForceUpdate(forceUpdate);
        v.setChangelog(changelog != null ? changelog : "");
        v.setDownloadUrl(apkReady ? base + "/app/" + apkFileName : "");
        return v;
    }

    @Data
    public static class AppVersionResponse {
        private int     versionCode;
        private String  versionName;
        private boolean forceUpdate;
        private String  changelog;
        private String  downloadUrl;
    }
}
