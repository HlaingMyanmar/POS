package org.sspd.servicemgmt.api;

import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppVersionSettingsService versionSettingsService;

    @Value("${app.download.base-url}")
    private String downloadBaseUrl;

    @Value("${app.download.apk-origin:http://118.27.151.89}")
    private String apkDownloadOrigin;

    @Value("${app.apk.storage-dir}")
    private String apkStorageDir;

    @GetMapping("/version")
    public ResponseEntity<ApiResponse<AppVersionResponse>> getVersion(HttpServletRequest request) {
        AppVersionSettings s = versionSettingsService.getOrCreate();
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", toResponse(
                s.getVersionCode(),
                s.getVersionName(),
                s.isForceUpdate(),
                s.getChangelog(),
                "servicemgmt.apk",
                request
        )));
    }

    @GetMapping("/technician/version")
    public ResponseEntity<ApiResponse<AppVersionResponse>> getTechnicianVersion(HttpServletRequest request) {
        AppVersionSettings s = versionSettingsService.getOrCreate();
        return ResponseEntity.ok(new ApiResponse<>(true, "OK", toResponse(
                s.getTechnicianVersionCode() == null ? 1 : s.getTechnicianVersionCode(),
                s.getTechnicianVersionName() == null ? "1.0.0" : s.getTechnicianVersionName(),
                s.isTechnicianForceUpdate(),
                s.getTechnicianChangelog(),
                "technician.apk",
                request
        )));
    }

    private AppVersionResponse toResponse(
            Integer versionCode,
            String versionName,
            boolean forceUpdate,
            String changelog,
            String apkFileName,
            HttpServletRequest request
    ) {
        boolean apkReady = new File(apkStorageDir, apkFileName).exists();
        AppVersionResponse v = new AppVersionResponse();
        v.setVersionCode(versionCode == null ? 0 : versionCode);
        v.setVersionName(versionName == null ? "" : versionName);
        v.setForceUpdate(forceUpdate);
        v.setChangelog(changelog != null ? changelog : "");
        v.setDownloadUrl(apkReady ? apkPublicOrigin(request) + "/app/" + apkFileName : "");
        return v;
    }

    /**
     * Phones reach the API by IP. Returning a domain here makes APK download fail
     * with "Unable to resolve host sspdmyanmar.com".
     */
    private String apkPublicOrigin(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        host = host == null ? "" : host.trim().replace(" ", "");
        String hostname = host;
        int colon = host.indexOf(':');
        if (colon > 0) {
            hostname = host.substring(0, colon);
        }
        if (hostname.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) {
            return "http://" + hostname;
        }
        String configured = firstNonBlank(apkDownloadOrigin, downloadBaseUrl, "http://118.27.151.89");
        configured = configured.trim().replace(" ", "").replaceAll("/+$", "");
        String lower = configured.toLowerCase(Locale.ROOT);
        if (lower.contains("sspdmyanmar.com") || !looksLikeIpOrigin(configured)) {
            return "http://118.27.151.89";
        }
        return configured;
    }

    private static boolean looksLikeIpOrigin(String origin) {
        String host = origin.replaceFirst("^https?://", "");
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon > 0) {
            host = host.substring(0, colon);
        }
        return host.matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || "localhost".equals(host) || "127.0.0.1".equals(host);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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
