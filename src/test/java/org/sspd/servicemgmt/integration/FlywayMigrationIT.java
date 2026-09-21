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
        assertTrue(latest != null && latest >= 175,
                "Expected Flyway version >= 175 but was " + latest);

        Integer jobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'service_jobs'",
                Integer.class);
        assertTrue(jobs != null && jobs == 1, "service_jobs table should exist after migrations");

        Integer bookings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'bookings'",
                Integer.class);
        assertTrue(bookings != null && bookings == 1, "bookings table should exist after migrations");

        Integer bookingItemComponents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'booking_item_components'",
                Integer.class);
        assertTrue(bookingItemComponents != null && bookingItemComponents == 1,
                "booking_item_components (V169) should exist");

        Integer bookingSettings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'service_booking_settings'",
                Integer.class);
        assertTrue(bookingSettings != null && bookingSettings == 1,
                "service_booking_settings (V170) should exist");

        Integer arrivalWindows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'service_booking_arrival_windows'",
                Integer.class);
        assertTrue(arrivalWindows != null && arrivalWindows == 1,
                "service_booking_arrival_windows (V173) should exist");

        Integer latestBookingColumns = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = DATABASE()
                           AND table_name = 'bookings'
                           AND column_name IN ('service_price_snapshot', 'rejection_reason', 'rejected_at', 'rejected_by')
                        """,
                Integer.class);
        assertTrue(latestBookingColumns != null && latestBookingColumns == 4,
                "bookings columns from V174-V175 should exist");

        Integer refreshSessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'refresh_sessions'",
                Integer.class);
        assertTrue(refreshSessions != null && refreshSessions == 1, "refresh_sessions (V164/V167) should exist");

        Integer loginAttempts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'login_attempt_state'",
                Integer.class);
        assertTrue(loginAttempts != null && loginAttempts == 1, "login_attempt_state (V166) should exist");

        Integer usernameNullable = jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = DATABASE()
                           AND table_name = 'users'
                           AND column_name = 'username'
                           AND is_nullable = 'NO'
                        """,
                Integer.class);
        assertTrue(usernameNullable != null && usernameNullable == 1,
                "users.username should be NOT NULL after V165");
    }
}
