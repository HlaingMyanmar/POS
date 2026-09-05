package org.sspd.servicemgmt.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedConfigurationSafetyTest {

    @Test
    void applicationPropertiesUsesPlaceholdersInsteadOfSecretsOrHostPaths() throws Exception {
        String props = Files.readString(Path.of("src/main/resources/application.properties"));

        assertTrue(props.contains("${DB_PASSWORD"));
        assertTrue(props.contains("${JWT_SECRET"));
        assertTrue(props.contains("${SSL_ENABLED"));
        assertTrue(props.contains("${APP_BASE_URL"));
        assertTrue(props.contains("${APP_APK_STORAGE_DIR"));
        assertTrue(props.contains("${APP_PRINT_TEMPLATES_DIR"));
        assertTrue(props.contains("${BACKUP_ROOT_DIRECTORY"));
        assertTrue(props.contains("${CORS_ALLOWED_ORIGINS"));
        assertTrue(props.contains("server.ssl.enabled=${SSL_ENABLED:false}"));
        assertTrue(props.contains("spring.flyway.clean-disabled=true"));
        assertTrue(props.contains("spring.jpa.hibernate.ddl-auto=validate"));

        assertFalse(props.contains("C:/sspd-apk"));
        assertFalse(props.contains("192.168."));
        assertFalse(props.contains("21101998"));
        assertFalse(props.contains("servicemgmt2024"));
    }

    @Test
    void productionProfileDisablesEmbeddedSslAndUsesLinuxPaths() throws Exception {
        String props = Files.readString(Path.of("src/main/resources/application-prod.properties"));
        assertTrue(props.contains("server.ssl.enabled=${SSL_ENABLED:false}"));
        assertTrue(props.contains("server.address=${SERVER_ADDRESS:127.0.0.1}"));
        assertTrue(props.contains("/opt/sspd/apk"));
        assertTrue(props.contains("/opt/sspd/print"));
        assertTrue(props.contains("/opt/sspd/Backup"));
        assertTrue(props.contains("spring.flyway.clean-disabled=true"));
        assertFalse(props.contains("C:/"));
        assertFalse(props.contains("192.168."));
    }
}
