package org.sspd.servicemgmt.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.sspd.servicemgmt.support.AbstractMysqlIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
class FlywayMigrationIT extends AbstractMysqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedThroughLatestMigrations() {
        Integer latest = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(version, 1) AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1",
                Integer.class);
        assertTrue(latest != null && latest >= 159,
                "Expected Flyway version >= 159 but was " + latest);

        Integer jobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'service_jobs'",
                Integer.class);
        assertTrue(jobs != null && jobs == 1, "service_jobs table should exist after migrations");

        Integer bookings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'bookings'",
                Integer.class);
        assertTrue(bookings != null && bookings == 1, "bookings table should exist after migrations");
    }
}
