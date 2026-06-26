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
        String base = downloadBaseUrl.endsWith("/")
                ? downloadBaseUrl.substring(0, downloadBaseUrl.length() - 1)
                : downloadBaseUrl;

        boolean apkReady = new File(apkStorageDir, "servicemgmt.apk").exists();

        AppVersionResponse v = new AppVersionResponse();
        v.setVersionCode(s.getVersionCode());
        v.setVersionName(s.getVersionName());
        v.setForceUpdate(s.isForceUpdate());
        v.setChangelog(s.getChangelog() != null ? s.getChangelog() : "");
        v.setDownloadUrl(apkReady ? base + "/app/servicemgmt.apk" : "");

        return ResponseEntity.ok(new ApiResponse<>(true, "OK", v));
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
