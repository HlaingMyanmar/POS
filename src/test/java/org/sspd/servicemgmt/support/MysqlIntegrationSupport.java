package org.sspd.servicemgmt.support;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Chooses MySQL for {@code *IT} tests:
 * <ul>
 *   <li>Testcontainers when Docker is available</li>
 *   <li>Local MySQL ({@code ser_db_it}) when {@code IT_USE_LOCAL_MYSQL=true}</li>
 *   <li>Otherwise tests are skipped</li>
 * </ul>
 */
public final class MysqlIntegrationSupport {

    public static final String LOCAL_JDBC =
            "jdbc:mysql://localhost:3306/ser_db_it?createDatabaseIfNotExist=true"
                    + "&rewriteBatchedStatements=true&useServerPrepStmts=false";

    private static final boolean USE_LOCAL =
            "true".equalsIgnoreCase(System.getenv("IT_USE_LOCAL_MYSQL"))
                    || Boolean.getBoolean("it.useLocalMysql");

    private static final boolean DOCKER_AVAILABLE = detectDocker();

    private MysqlIntegrationSupport() {
    }

    public static boolean useLocalMysql() {
        return USE_LOCAL;
    }

    public static boolean dockerAvailable() {
        return DOCKER_AVAILABLE;
    }

    public static boolean mysqlIntegrationEnabled() {
        return USE_LOCAL || DOCKER_AVAILABLE;
    }

    public static void assumeMysqlAvailable() {
        Assumptions.assumeTrue(mysqlIntegrationEnabled(),
                "Skip IT: set IT_USE_LOCAL_MYSQL=true (local MySQL ser_db_it) or install Docker for Testcontainers");
    }

    public static MySQLContainer<?> newMysqlContainer() {
        return new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("ser_db_it")
                .withUsername("test")
                .withPassword("test");
    }

    private static boolean detectDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
