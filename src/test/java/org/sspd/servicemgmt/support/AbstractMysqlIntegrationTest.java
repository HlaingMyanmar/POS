package org.sspd.servicemgmt.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Base for MySQL/Flyway {@code *IT} tests.
 * Uses local {@code ser_db_it} when {@code IT_USE_LOCAL_MYSQL=true}, otherwise Testcontainers.
 */
public abstract class AbstractMysqlIntegrationTest {

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = initContainer();

    private static MySQLContainer<?> initContainer() {
        if (MysqlIntegrationSupport.useLocalMysql() || !MysqlIntegrationSupport.dockerAvailable()) {
            return null;
        }
        MySQLContainer<?> container = MysqlIntegrationSupport.newMysqlContainer();
        container.start();
        return container;
    }

    @BeforeAll
    static void requireMysqlBackend() {
        MysqlIntegrationSupport.assumeMysqlAvailable();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        if (MysqlIntegrationSupport.useLocalMysql()) {
            String url = System.getenv().getOrDefault("IT_DB_URL", MysqlIntegrationSupport.LOCAL_JDBC);
            // Force a real MySQL account — blank username makes the JDBC driver fall back to the OS user.
            String username = firstNonBlank(
                    System.getenv("IT_DB_USERNAME"),
                    System.getenv("DB_USERNAME"),
                    secret("spring.datasource.username"),
                    secret("DB_USERNAME"),
                    "root");
            String password = firstNonBlank(
                    System.getenv("IT_DB_PASSWORD"),
                    System.getenv("DB_PASSWORD"),
                    secret("spring.datasource.password"),
                    secret("DB_PASSWORD"));
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
            return;
        }
        if (MYSQL != null) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static String secret(String key) {
        Properties props = loadLocalSecrets();
        String value = props.getProperty(key);
        return value == null ? "" : value.trim();
    }

    private static Properties loadLocalSecrets() {
        Properties props = new Properties();
        for (Path path : new Path[] {
                Path.of("application-secrets.properties"),
                Path.of(".env")
        }) {
            if (!Files.isRegularFile(path)) continue;
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException ignored) {
                // ITs fail with Access denied if secrets are missing
            }
        }
        return props;
    }
}
