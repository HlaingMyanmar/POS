package org.sspd.servicemgmt.config;

import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Profile("prod")
@Slf4j
public class ProductionConfigValidator implements ApplicationRunner {

    private static final String WELL_KNOWN_DEMO_JWT =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${application.security.jwt.secret-key:}")
    private String jwtSecret;

    @Value("${app.download.base-url:}")
    private String appBaseUrl;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${app.apk.storage-dir}")
    private String apkStorageDir;

    @Value("${app.print.templates-dir:}")
    private String printTemplatesDir;

    @Value("${app.booking-photo.storage-dir}")
    private String bookingPhotoStorageDir;

    @Value("${backup.root-directory}")
    private String backupRootDirectory;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("Production requires DB_PASSWORD.");
        }
        if (jwtSecret == null || jwtSecret.isBlank() || "CHANGE_ME".equals(jwtSecret)
                || WELL_KNOWN_DEMO_JWT.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "Production requires a unique JWT_SECRET (Base64 of at least 32 random bytes).");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JWT_SECRET must be Base64-encoded.", ex);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes.");
        }
        if (appBaseUrl == null || appBaseUrl.isBlank() || appBaseUrl.contains("CHANGE_ME")) {
            throw new IllegalStateException("Production requires APP_BASE_URL, for example https://your-domain.com");
        }
        if (sslEnabled) {
            log.warn("server.ssl.enabled=true in prod. Expected Nginx TLS termination and SSL_ENABLED=false.");
        }
        Files.createDirectories(Path.of(apkStorageDir));
        Files.createDirectories(Path.of(bookingPhotoStorageDir));
        Files.createDirectories(Path.of(backupRootDirectory));
        if (printTemplatesDir != null && !printTemplatesDir.isBlank()
                && !Files.isDirectory(Path.of(printTemplatesDir))) {
            log.warn("Print templates dir {} is missing — WAR voucher templates will be used", printTemplatesDir);
        }
        log.info("Production configuration validated. APK dir={}, print dir={}, backup dir={}, public URL={}",
                apkStorageDir, printTemplatesDir, backupRootDirectory, appBaseUrl);
    }
}
